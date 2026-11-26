# Định nghĩa và lập luận của bản sửa

## 1. Dữ liệu và thứ tự

Mỗi transaction t là ánh xạ hữu hạn item -> p(i,t), 0 < p <= 1; một item xuất hiện tối đa một lần. ExpSup(X) = sum_{t chứa X} product_{i in X} p(i,t), theo giả định item independence trong transaction. Miền P gồm các X khác rỗng với ít nhất một transaction hỗ trợ. Đây là expected-support mining, không phải mô hình tương quan tùy ý hoặc xác suất tail của support.

sigma(i)=ExpSup({i}). Thứ tự toàn cục giảm sigma và tăng item ID khi hòa. a(X) là item cuối theo thứ tự này. Prefix(t,a) gồm tất cả items trước a trong transaction, không chỉ các items của X. Một node/class v ứng với chuỗi prefix nhãn giống hệt và cùng anchor a.

M1(t,a), M2(t,a) là hai xác suất lớn nhất trong prefix; hai vị trí có giá trị bằng nhau được tính là hai phần tử. Nếu thiếu thì dùng 0. C_v=sum_{t trong v} p(a,t)*M1(t,a); R_v=max_{t trong v} M2(t,a). Với các node cùng nhãn, các tập TID rời nhau.

L(X) là tập node có anchor a(X) và prefix chứa X\{a(X)}. Với r>=2:

B(X)=sum_{v in L(X)} C_v * R_v^(r-2).

B({i})=sigma(i). Khi r=2, nhân tử lũy thừa bằng 1, kể cả R=0. Một join giữ nguyên C,R,pre,post của node con; không lấy max proxy giữa node cha và con. R có thể là max từ transaction khác cùng class và vẫn là cận hợp lệ.

## 2. Cận và điều kiện cắt nhánh

Với transaction hỗ trợ X có r>=2, chọn xác suất lớn nhất trong r-1 item tiền tố thuộc X. Xác suất đó <=M1; r-2 xác suất còn lại <=M2<=R_v. Suy ra product p(i,t) <= p(a,t)*M1*R_v^(r-2). Cộng theo transaction/node cho ExpSup(X)<=B(X). Với singleton, dùng sigma trực tiếp.

Mặt khác M1<=1 và R<=1, nên B(X)<=sum_{t chứa X}p(a,t)<=sigma(a(X)). Thứ tự toàn cục làm sigma không tăng khi anchor được mở rộng về phía sau. Vì thế U(P)=sigma(a(P)) chặn B(Y) của mọi descendant Y theo phép mở rộng chuẩn. Code NTUFP dùng U(P)<theta để cắt cây con; vẫn giữ ứng viên B(P)<theta nếu U(P)>=theta. B(P) tự nó không là cận cây con khi anchor thay đổi.

Hybrid xếp hạng theo ExpSup. Vì mọi p<=1, ExpSup(Y)<=ExpSup(P) cho Y chứa P. Do đó exact support của P và các sibling là cận cây con hợp lệ. Metadata cap B(Y)<theta loại Y và descendants bằng ExpSup(Y)<=B(Y), trước khi materialize TID. Không sử dụng giả định B anti-monotone.

TUFP giữ CUP-list: entry chứa q_X(t)=product p(i,t) và q_prefix(t). Maximum conditional probability là max q_X/q_prefix = max p(last(X),t). Với A=P+a, B=P+b, ExpSup(A union B)<=ExpSup(A)*maxConditional(B). Exact join dùng q_A(t)*p(b,t) từ dữ liệu decimal đã group, tránh phép chia làm tròn prefix nhỏ. Đây là bản sửa baseline được cung cấp, không phải xác nhận tính trung thành của mã này với implementation chính thức của bài TUFP gốc.

## 3. Bất biến join

List node sắp theo preorder. Các node có cùng anchor là antichain vì một item không lặp trên một đường đi. Hai con trỏ ghép cha/con bằng pre(parent)<pre(child), post(parent)>post(child). Mỗi node con có tối đa một node cha cùng nhãn trong list cha.

Trong một lớp tương đương tiền tố, A=P+a, B=P+b với a đứng trước b. TID payload của node con B là tập con của payload cha A tương ứng. Entry chứa (tid,q_X(t),p(a(X),t)). Khi ghép:

q_(P+a+b)(t) = q_(P+b)(t) * p(a,t).

preProb mới giữ p(b,t), không phải q_(P+b)(t). Payload bất biến, TIDs tăng và không lặp. Nếu mất bất biến TID subset, code báo lỗi thay vì âm thầm bỏ transaction.

Metadata join chỉ lưu references node và tính B. Sau khi qua cap, materializeExact mới nhân xác suất và tạo payload mới. Counter metadataPairs phân hoạch thành capRejectedPairs và tidPairMerges. Trong package này test đã kiểm tra đẳng thức đó trên mọi lần chạy Hybrid và ghi nhận các cap rejection thực sự.

## 4. Heap và phép liệt kê

Các singleton đều tham gia cấu trúc tìm kiếm; heap mới bị giới hạn k. Một itemset được sinh đúng một lần bằng mở rộng chuẩn trong lớp tiền tố. List rỗng bị bỏ trước khi offer. BigDecimal không dùng MathContext hoặc scale cố định trong phép cộng/nhân/lũy thừa.

theta=0 khi heap chưa đủ k; sau đó theta là score của phần tử tệ nhất trong heap. Score tăng dần ở root heap theo comparator đảo của thứ tự tốt nhất: score giảm, cardinality tăng, identifier-lex tăng. Mọi phép cắt chỉ dùng score < theta; dấu bằng phải được duyệt để thực hiện tie rule. Bộ đệm so toàn bộ comparator khi thay phần tử. Ngưỡng score không giảm khi heap đầy.

Từ cận hợp lệ, phép liệt kê chuẩn đầy đủ, heap size và tie rule, các phép cắt không thể bỏ một mẫu được xếp tốt hơn phần tử cuối cần giữ. Đây là lập luận cho contract nêu trên; bộ test Fraction độc lập kiểm tra implementation trên các trường hợp hữu hạn.

## 5. Chi phí và phạm vi chứng minh

Chuẩn bị dữ liệu dùng maps và sắp thứ tự item; không dùng các mảng theo max item ID hoặc max TID. Node lookup dùng hash map; duyệt children theo global rank. Một metadata join có hai con trỏ tuyến tính theo chiều dài hai lists. Với một cặp node sống sót, merge TID dùng hai con trỏ và có chi phí tuyến tính theo chiều dài payload; phép toán BigDecimal có chi phí phụ thuộc số chữ số, không thể coi mặc nhiên là O(1). Cùng một ancestor payload có thể được quét lại khi ghép nhiều node con; không tuyên bố tổng toàn bộ các merge chỉ là một lần quét database.

Các bound không bảo đảm số itemsets đa thức. Search vẫn có thể duyệt số lượng mẫu mũ, đặc biệt khi k lớn, nhiều score hòa, hoặc U của NTUFP lỏng. DFS/tree construction cũng bị giới hạn bởi stack/heap JVM trên dữ liệu rất sâu. Không có kiểm chứng scalability hay chứng minh ưu thế thời gian/bộ nhớ từ các test nhỏ. Không khẳng định approximation ratio hoặc chất lượng TopK_ExpSup cho NTUFP chỉ vì TopK_B đúng.

Các biến Constant.KValue và MemoryLogger vẫn là cấu hình dùng chung để tương thích. Các entry point và test hỗ trợ chạy tuần tự; không dùng chung cấu hình mutable để chạy nhiều miner đồng thời trong cùng JVM.
