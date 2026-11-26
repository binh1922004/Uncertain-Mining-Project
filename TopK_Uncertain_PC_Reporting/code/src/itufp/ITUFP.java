package itufp;

import java.math.BigDecimal;
import java.util.*;

/** Independent reconstruction of Davashi (2023), Figs. 7–8. */
public final class ITUFP {
    static final BigDecimal ZERO = BigDecimal.ZERO;
    public record Result(List<Integer> items, BigDecimal support) {}
    public record Tuple(int tid, BigDecimal probability) {}
    public static final Comparator<Result> RANK = (a,b) -> {
        int c = b.support.compareTo(a.support);
        if(c != 0) return c;
        c = Integer.compare(a.items.size(), b.items.size());
        if(c != 0) return c;
        for(int i=0;i<a.items.size();i++) {
            c = Integer.compare(a.items.get(i), b.items.get(i));
            if(c != 0) return c;
        }
        return 0;
    };
    static final class TopK {
        final int k;
        final ArrayList<Result> tk = new ArrayList<>();
        TopK(int k) { this.k=k; }
        BigDecimal threshold() { return tk.size()==k ? tk.get(k-1).support : ZERO; }
        void offer(UPList l) {
            if(!l.complete || l.records.isEmpty() || l.expSup.compareTo(threshold())<0) return;
            Result r=new Result(l.ids,l.expSup);
            if(tk.size()==k && RANK.compare(r,tk.get(k-1))>=0) return;
            if(tk.size()==k) tk.remove(k-1);
            tk.add(r);
            tk.sort(RANK);
        }
    }
    /** UP-List or IMCUP-List. Cursors are next unprocessed offsets, not last indices. */
    static final class UPList {
        final List<Integer> ids; // numeric order for result ties
        final int suffix; // final item in mining order
        final UPList left, right;
        final ArrayList<Tuple> records = new ArrayList<>();
        final Map<Integer,UPList> children = new LinkedHashMap<>();
        BigDecimal expSup=ZERO, maxJoint=ZERO, maxConditional=ZERO;
        int next1=0,next2=0;
        boolean complete;
        UPList(List<Integer> ids,int suffix,UPList left,UPList right) {
            this.ids=List.copyOf(ids); this.suffix=suffix; this.left=left; this.right=right;
        }
        void append(int tid, BigDecimal q,BigDecimal p) {
            records.add(new Tuple(tid,q)); expSup=expSup.add(q);
            maxJoint=maxJoint.max(q); maxConditional=maxConditional.max(p);
        }
    }
    public static final class Stats {
        public long eligiblePairs, exactCandidateJoins;
        public long newLists, resumedLists, reusedComplete, pufpRejections, ppfStops, matchedTuples;
        @Override public String toString() {return "new_lists="+newLists+" resumed_lists="+resumedLists+
            " reused_complete="+reusedComplete+" pufp_rejections="+pufpRejections+
            " ppf_stops="+ppfStops+" matched_tuples="+matchedTuples;}
    }
    final List<Map<Integer,BigDecimal>> db;
    final ArrayList<UPList> roots=new ArrayList<>();
    final Runnable sample;
    public Stats stats=new Stats();
    private int largestK=0;
    private List<Result> cached=List.of();

    /** db is validated and must remain unchanged throughout the session. */
    public ITUFP(List<Map<Integer,BigDecimal>> db,Runnable sample) {
        this.db=db; this.sample=Objects.requireNonNull(sample);
        Map<Integer,UPList> single=new HashMap<>();
        for(int tid=0;tid<db.size();tid++) {
            for(var e:db.get(tid).entrySet()) {
                int a=e.getKey(); BigDecimal p=e.getValue();
                UPList l=single.computeIfAbsent(a,x->new UPList(List.of(x),x,null,null));
                l.append(tid,p,p);
            }
        }
        roots.addAll(single.values());
        for(UPList l:roots) l.complete=true;
        roots.sort((a,b)->{int c=b.expSup.compareTo(a.expSup); return c!=0?c:Integer.compare(a.suffix,b.suffix);});
        sample.run();
    }
    public List<Result> query(int k) {
        if(k<1) throw new IllegalArgumentException("k must be positive");
        stats=new Stats();
        if(k<=largestK) return List.copyOf(cached.subList(0,Math.min(k,cached.size())));
        TopK top=new TopK(k);
        for(int i=0;i<Math.min(k,roots.size());i++) top.offer(roots.get(i));
        growth(roots,top);
        sample.run();
        largestK=k; cached=List.copyOf(top.tk);
        return cached;
    }
    /** Canonical sibling growth; only complete lists may act as parents. */
    private void growth(List<UPList> cls, TopK top) {
        for(int i=0;i<cls.size();i++) {
            UPList a=cls.get(i);
            if(!a.complete) throw new IllegalStateException("incomplete parent");
            if(a.expSup.compareTo(top.threshold())<0) continue;
            ArrayList<UPList> next=new ArrayList<>();
            for(int j=i+1;j<cls.size();j++) {
                UPList b=cls.get(j);
                if(!b.complete) throw new IllegalStateException("incomplete parent");
                if(b.expSup.compareTo(top.threshold())<0) continue;
                stats.eligiblePairs++;
                // PUFP: q(Pab,t)=q(Pa,t)*p(b,t); joint max of Pb is unsafe here.
                if(a.expSup.multiply(b.maxConditional).compareTo(top.threshold())<0) {
                    stats.pufpRejections++; continue;
                }
                UPList c=a.children.get(b.suffix);
                if(c==null) {
                    ArrayList<Integer> ids=new ArrayList<>(a.ids); ids.add(b.suffix); ids.sort(Integer::compare);
                    c=new UPList(ids,b.suffix,a,b); a.children.put(b.suffix,c); stats.newLists++;
                } else if(c.left!=a || c.right!=b) throw new IllegalStateException("noncanonical join");
                stats.exactCandidateJoins++;
                finish(c,top);
                if(c.complete && !c.records.isEmpty()) {
                    top.offer(c);
                    if(c.expSup.compareTo(top.threshold())>=0) next.add(c);
                }
            }
            sample.run();
            if(next.size()>1) growth(next,top);
        }
    }
    /** ppf bound uses the first parent's maximum joint probability, as in Fig. 4. */
    private boolean stop(UPList c,TopK top) {
        BigDecimal ub=c.expSup.add(c.left.maxJoint.multiply(BigDecimal.valueOf(c.left.records.size()-c.next1)));
        if(ub.compareTo(top.threshold())<0) {stats.ppfStops++; return true;}
        return false;
    }
    /** PUT: continue a previously interrupted merge without revisiting processed tuples. */
    private void finish(UPList c,TopK top) {
        if(c.complete) {stats.reusedComplete++;return;}
        UPList a=c.left,b=c.right;
        if(c.next1!=0 || c.next2!=0) {
            if(stop(c,top)) return;
            stats.resumedLists++;
        }
        while(c.next1<a.records.size() && c.next2<b.records.size()) {
            Tuple x=a.records.get(c.next1), y=b.records.get(c.next2);
            if(x.tid<y.tid) c.next1++;
            else if(x.tid>y.tid) {c.next2++;continue;}
            else {
                BigDecimal p=db.get(x.tid).get(b.suffix);
                if(p==null) throw new IllegalStateException("missing suffix");
                c.append(x.tid,x.probability.multiply(p),p);
                c.next1++;c.next2++;stats.matchedTuples++;
            }
            if(c.next1<a.records.size() && c.next2<b.records.size() && c.next1%2==0 && stop(c,top)) return;
        }
        c.complete=true; // either full parent exhausted; no more possible matches
    }
}
