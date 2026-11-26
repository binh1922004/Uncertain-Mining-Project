# Đối chiếu sửa mã với kiểm toán gốc

C01–C16 đã sửa theo hợp đồng và được kiểm tra trong phạm vi ghi ở verification/summary.json. C17 là giới hạn bằng chứng còn mở. Vị trí dòng của bản cũ ở audit_findings.json; toàn bộ diff ở patches/original_to_fixed.patch.

## C01 — Hybrid dùng cap của singleton để cắt nhánh exact

**[Vị trí / Vấn đề]**: algo/TNUFPHybridSolution/TNUFPHybridAlgo.java:62,71; WNTree/TPNList.java:11–16,29–36.

- **Minh chứng gốc**: if (tpnListA.getExpSup(I.get(i).getItem().size()).compareTo(minSup) < 0) continue; getExpSup(1) trả tổng tPCap, trong khi singleton cần σ(a)=ExpSup({a}).
- **Phân tích rủi ro**: Tái hiện singleton_cap: T1={1:0.8,3:0.4}, T2={1:0.1,3:0.3,2:0.8,4:0.5}, k=5. Kết quả đúng có {2,4}:0.40; mã Hybrid giữ {1,3}:0.35 và bỏ {2,4}. Đây là mất mẫu có điểm cao hơn, hoàn toàn không phải sai khác do ties. σ(2)=0.8, nhưng cap metadata của nút 2 chỉ là 0.08. Sửa riêng cách đánh giá singleton trong bản chẩn đoán làm ca này trả đúng.
- **Điều chỉnh đã thực hiện**: Định nghĩa singletonSupport=sigma; Hybrid dùng exact support để cắt nhánh. Không dùng tổng C_v của singleton làm sigma.
  Vị trí bản sửa: `src/WNTree/TPNList.java; src/utils/TPNMiningEngine.java`.

## C02 — Nâng ngưỡng khi heap chưa đủ k

**[Vị trí / Vấn đề]**: algo/WUIP/WUIPAlgo.java:96–101; algo/TUFP/TUFPAlgo.java:124–130.

- **Minh chứng gốc**: minSup = topKUFPs.peek().getExpSup();
- **Phân tích rủi ro**: Tái hiện underfilled_heap: một giao dịch {1:0.9,2:0.8,3:0.1}, k=10. Có 7 itemsets có support dương. WUIP và TUFP chỉ trả 4, bỏ {1,3}, {2,3}, {1,2,3}; Hybrid trả đủ 7. Sau cặp đầu tiên, hai bản cài đặt nâng ngưỡng lên 0.1 dù heap mới có 4 phần tử. Bản chẩn đoán chỉ bổ sung điều kiện heap đủ k làm cả hai trả đủ 7.
- **Điều chỉnh đã thực hiện**: threshold() bằng 0 trước khi đủ k; chỉ dùng score root khi heap đầy.
  Vị trí bản sửa: `src/utils/TopKBuffer.java`.

## C03 — Hai hàm tính cùng cap nhưng dùng số mũ khác nhau

**[Vị trí / Vấn đề]**: WNTree/TPNList.java:29–36; DataObject/Operation.java:112–114.

- **Minh chứng gốc**: getExpSupHigher2(size) đối lập với getExpSupHigher2(sz - 2).
- **Phân tích rủi ro**: Unit test trực tiếp trên mã gốc với TpCap=0.4, pProxy=0.5 và r=3: Operation.calExpSup trả 0.20; TPNList.getExpSup trả 0.0500. Trường hợp một đường xác suất 0.8,0.5,0.5 cho thấy support triple có thể là 0.20; giá trị 0.05 không còn là upper bound. getExpSup được dùng để cắt nhánh ở cả WUIP và Hybrid. Unit test xác nhận bất nhất số mũ; không gán toàn bộ các ca mất mẫu cho riêng lỗi này vì các lỗi cắt nhánh khác cũng cùng tồn tại.
- **Điều chỉnh đã thực hiện**: Mọi API nhận cardinality r; số mũ duy nhất r-2, xử lý r=1 và r=2 tách biệt.
  Vị trí bản sửa: `src/WNTree/TPNList.java; src/DataObject/Operation.java`.

## C04 — WUIP loại extension chỉ vì cap hiện tại nhỏ

**[Vị trí / Vấn đề]**: algo/WUIP/WUIPAlgo.java:70–71,79–80,87–89,97.

- **Minh chứng gốc**: if (expSup.compareTo(minSup) < 0 || (topKUFPs.size() == Constant.KValue && expSup.compareTo(minSup) <= 0)) { continue; } Inext.add(tpnListAB) chỉ được thực hiện sau điều kiện này.
- **Phân tích rủi ro**: Tái hiện nonmonotone_branch: T1={4:0.9,5:0.8,1:0.1,2:0.1,3:0.9}, T2={1:1}, T3={2:1}, T4={4:1,5:1}, k=16. Thứ tự cố định là 4≺5≺1≺2≺3. Theo định nghĩa B của bản thảo: B({1,2})=0.09 nhưng B({1,2,3})=0.648. Một bản chẩn đoán đã sửa riêng ngưỡng heap, số mũ và proxy vẫn bỏ ba triple có B=0.648, giữ các mẫu bốn items có B=0.5184. Như vậy lỗi này tồn tại độc lập với ba lỗi trên. Lưu ý đây là kiểm tra TopK_B của nhánh surrogate, không đòi NTUFP phải trả TopK_ExpSup.
- **Điều chỉnh đã thực hiện**: Dùng envelope U(P)=sigma(anchor(P)) cho NTUFP. Giữ nhánh có B(P)<theta nếu U(P)>=theta; xem lập luận trong ALGORITHM_CONTRACT.md.
  Vị trí bản sửa: `src/utils/TPNMiningEngine.java`.

## C05 — Intersection thay proxy gốc bằng max của hai nút

**[Vị trí / Vấn đề]**: DataObject/Operation.java:30–31,55,80–86.

- **Minh chứng gốc**: BigDecimal mx = tpnCodeA.getpProxy().max(tpnCodeB.getpProxy());
- **Phân tích rủi ro**: Unit test: proxy nút con 0.2 bị thay thành 0.9 từ nút cha. Tái hiện proxy_metadata trong bộ kiểm thử: score WUIP của {3,4,6} là 0.720 trong khi B theo định nghĩa đã thống nhất là 0.480; mẫu này đẩy singleton {1}:0.700 ra khỏi top-12. Bản chẩn đoán chỉ giữ proxy nút con trả đúng ca này. Proxy lớn hơn có thể vẫn cho một upper bound lỏng hơn khi các xác suất hợp lệ; bản thân phép max này không chứng minh Hybrid tính sai ExpSup. Sai lệch chắc chắn là không giữ metadata và không xếp hạng đúng B đã định nghĩa.
- **Điều chỉnh đã thực hiện**: Join giữ nguyên proxy của node anchor con; metadata và TID payload bất biến.
  Vị trí bản sửa: `src/DataObject/Operation.java`.

## C06 — Thứ tự item đồng support không thống nhất giữa các giao dịch

**[Vị trí / Vấn đề]**: WNTree/TPNTree.java:80,92.

- **Minh chứng gốc**: transactions[i].sort((a, b) -> (expSup.get(b).compareTo(expSup.get(a))));
- **Phân tích rủi ro**: Tái hiện equal_support_input_order: T1 ghi 1:0.5 rồi 2:0.5; T2 ghi 2:0.5 rồi 1:0.5; k=3. Hai singleton đều có ExpSup=1.0. Do comparator không phá ties, hai giao dịch đi hai đường ngược nhau. WUIP và Hybrid tính score/support của {1,2}=0.25 thay vì 0.50. TUFP trả 0.50 ở ca này. Đây là đầu vào với xác suất hợp lệ và không có item lặp.
- **Điều chỉnh đã thực hiện**: Một comparator sigma giảm, item ID tăng dùng thống nhất khi tạo tree, list và search.
  Vị trí bản sửa: `src/utils/PreparedDatabase.java; src/WNTree/TPNTree.java`.

## C07 — Heap và cắt tại dấu bằng không thực hiện quy tắc ties

**[Vị trí / Vấn đề]**: WUIPAlgo.java:20,87–89; TNUFPHybridAlgo.java:18,81; TUFPAlgo.java:23,108.

- **Minh chứng gốc**: new PriorityQueue<>(Comparator.comparing(Itemset::getExpSup)); các phép cắt có expSup.compareTo(minSup) <= 0.
- **Phân tích rủi ro**: Tái hiện tie_boundary: một giao dịch gồm bốn items 1,2,3,4, tất cả xác suất 1; k=8. Cả ba mã giữ {1,2,3} thay vì {2,3}. Mọi điểm bằng 1, nên đây không phải mất nghiệm có support cao hơn, nhưng trái Definition 9: ít items được ưu tiên trước, sau đó lexicographic identifier. Nếu không thống nhất ties, Jaccard thấp có thể xuất hiện giữa hai tập top-k cùng tối ưu về support.
- **Điều chỉnh đã thực hiện**: Xếp hạng đầy đủ theo score/cardinality/identifier-lex; chỉ prune ở dấu <, duyệt hòa điểm.
  Vị trí bản sửa: `src/utils/ResultRanking.java; src/utils/TopKBuffer.java`.

## C08 — Trả mẫu có giao dịch hỗ trợ rỗng

**[Vị trí / Vấn đề]**: WUIPAlgo.java:84–97; TNUFPHybridAlgo.java:75–103; TUFPAlgo.java:112–125.

- **Minh chứng gốc**: Không kiểm tra list giao có rỗng trước khi score 0 được đưa vào heap; TUFP chỉ kiểm tra intersection==null.
- **Phân tích rủi ro**: Tái hiện empty_intersection: T1={1:0.9}, T2={2:0.8}, k=3. Miền P được định nghĩa chỉ gồm itemsets có supporting transactions khác rỗng, nên kết quả có hai singleton. Cả ba mã trả thêm {1,2}:0, mặc dù không giao dịch nào chứa cặp đó. Việc kiểm tra score>=0 khi heap chưa đầy không đủ để kiểm tra tư cách thành viên P.
- **Điều chỉnh đã thực hiện**: Bỏ giao metadata/CUP rỗng. Heap chỉ nhận mẫu score dương trong miền được hỗ trợ.
  Vị trí bản sửa: `src/utils/TPNMiningEngine.java; src/algo/TUFP/TUFPAlgo.java`.

## C09 — TUFP làm tròn cố định có thể tạo chia cho 0

**[Vị trí / Vấn đề]**: algo/TUFP/TUFPAlgo.java:158; CUP/CUPList.java:65–69.

- **Minh chứng gốc**: (pA.multiply(pB)).divide(listA.get(i).getPreProbabilities(), 10, RoundingMode.HALF_UP);
- **Phân tích rủi ro**: Tái hiện tiny_probabilities: một giao dịch có ba items, mỗi xác suất 0.000001, k=7. Tích cặp 10^−12 bị làm tròn thành 0 ở 10 chữ số sau dấu chấm; phép giao sâu hơn tạo mẫu số 0 và chương trình ném ArithmeticException: / by zero. Cả ba xác suất đầu vào đều nằm trong miền hợp lệ. HALF_UP cũng không bảo đảm một giá trị dùng làm upper bound luôn được làm tròn lên.
- **Điều chỉnh đã thực hiện**: BigDecimal trực tiếp từ chuỗi; exact join nhân q_A với p(item mới); bỏ scale=10. Conditional quotient của CUP dùng chia chính xác, không làm tròn.
  Vị trí bản sửa: `src/algo/TUFP/TUFPAlgo.java; src/CUP/CUPList.java`.

## C10 — Các phép merge giả định TID đã tăng dần nhưng không sắp xếp

**[Vị trí / Vấn đề]**: algo/TUFP/TUFPAlgo.java:37–43,61–63,146–167; utils/ExactExpSupCalculator.java:38–42,79–96.

- **Minh chứng gốc**: tidSet.get(item).add(tid); sau đó dùng merge hai con trỏ mà không sort.
- **Phân tích rủi ro**: Tái hiện unsorted_tids với thứ tự records: (2,1,0.9), (1,2,0.8), (1,1,0.9), (2,2,0.8), k=3. TUFP tính {1,2}=0.72 thay vì 1.44. TPNTree/Hybrid đi qua TID theo chỉ số nên không gặp lỗi này trong ca thử. ExactExpSupCalculator cũng có giả định sorted TIDs; không nên lấy helper có sẵn này làm oracle độc lập cho input chưa chuẩn hóa.
- **Điều chỉnh đã thực hiện**: Group theo TID tăng bằng TreeMap; các payload/list được xây theo thứ tự đó, không phụ thuộc thứ tự records.
  Vị trí bản sửa: `src/utils/PreparedDatabase.java; src/utils/ExactExpSupCalculator.java`.

## C11 — Ngầm giả định identifiers là dãy 1 đến numberItems

**[Vị trí / Vấn đề]**: WNTree/TPNTree.java:75–78; algo/TUFP/TUFPAlgo.java:56–58.

- **Minh chứng gốc**: for (int i = 1; i <= Constant.numberItems; i++)
- **Phân tích rủi ro**: Tái hiện noncontiguous_ids: một giao dịch có identifiers 10 và 20, m=2, k=3. Cả ba mã trả rỗng thay vì hai singleton và một cặp. Definition 1 chỉ yêu cầu identifiers duy nhất và có total order, không yêu cầu bắt đầu từ 1 hoặc liên tiếp. Nếu định dạng .hui thực tế quy ước header là max ID thay vì số items, cần viết rõ và chứng minh pipeline remap; ZIP hiện không có tài liệu đó.
- **Điều chỉnh đã thực hiện**: Liệt kê identifiers thực tế, dùng rank map; không lặp 1..numberItems.
  Vị trí bản sửa: `src/utils/PreparedDatabase.java; src/WNTree/TPNTree.java; src/algo/TUFP/TUFPAlgo.java`.

## C12 — Parser và chuẩn hóa xác suất không bảo toàn dữ liệu hoặc báo lỗi đầy đủ

**[Vị trí / Vấn đề]**: IO/Input.java:23,28,32–43; DataObject/Transaction.java:10–15; TPNTree.java:55–60; TUFPAlgo.java:28–33.

- **Minh chứng gốc**: double proba = Double.parseDouble(...); while (proba > 1.0) { proba /= 10.0; } catch (FileNotFoundException e) { ... } return db;
- **Phân tích rủi ro**: Các ca thực thi riêng: p=2.0 bị đổi thành 0.2; p=Infinity làm treo vòng chuẩn hóa và bị timeout sau 2 giây; p=1e−400 bị double làm thành 0 rồi bị bỏ; duplicate (TID,item) được chấp nhận làm singleton score 1.8 từ hai dòng 0.9 trong cùng một transaction. File không tồn tại in stack trace nhưng trả DB rỗng và tiến trình harness vẫn exit 0. Dòng có hai dấu cách gây NumberFormatException. Với dữ liệu ngoài miền, vấn đề là thiếu từ chối rõ ràng; không suy diễn quy ước chuẩn hóa nào là ý định của tác giả.
- **Điều chỉnh đã thực hiện**: Đọc chuỗi decimal, kiểm tra miền, duplicate, header, TID và định dạng. Không tự normalize. Lỗi file/parse truyền ra caller; có exit code khác 0.
  Vị trí bản sửa: `src/IO/Input.java; src/DataObject/Transaction.java`.

## C13 — Hybrid nhân TID records trước khi kiểm tra cap

**[Vị trí / Vấn đề]**: TNUFPHybridAlgo.java:75–88; DataObject/Operation.java:18–19,57–76.

- **Minh chứng gốc**: TPNList tpnIntersection = Operation.Intersection(...); BigDecimal expSuppCap = Operation.calExpSup(...); intersectHybrid đã thực hiện combinedProb = entryA.getProb().multiply(entryB.getPreProb()).
- **Phân tích rủi ro**: Thứ tự thực thi trái Algorithm 2: phương pháp đề xuất lọc bằng metadata/cap trước MaterializeExact để tránh giao và nhân TID của ứng viên bị loại. Ở ZIP, chi phí đó đã được trả trước phép kiểm tra cap. Đây là sai lệch cơ chế và chi phí, không tự nó chứng minh score exact sai. Không thể dùng mã này để khẳng định thực nghiệm đã đo lợi ích tránh TID merge bằng cap filtering như bản thảo sửa.
- **Điều chỉnh đã thực hiện**: Tách joinMetadata/capFromMatches khỏi materializeExact. Ghi counters và kiểm tra metadataPairs=capRejectedPairs+tidPairMerges trên 2.998 lần chạy Hybrid.
  Vị trí bản sửa: `src/DataObject/Operation.java; src/utils/TPNMiningEngine.java`.

## C14 — work trả danh sách rỗng dù heap có kết quả

**[Vị trí / Vấn đề]**: WUIPAlgo.java:18,26,59; TNUFPHybridAlgo.java:16,24,53; MainWUIP.java:33–41.

- **Minh chứng gốc**: return WUIPs; nhưng WUIPs không được thêm itemsets trong hai lớp này.
- **Phân tích rủi ro**: ApiProbe với một giao dịch {1:0.9,2:0.8}, k=3: cả hai work trả size=0 trong khi getTopKUFPs có ba mẫu. MainWUIP in Size:0 và finalSize=0 vì đọc danh sách trả về, dù file output được ghi từ heap. Có thể gây sai log đếm hoặc caller nhận nhầm kết quả rỗng. Điều này không có nghĩa mọi file patterns xuất ra đều rỗng.
- **Điều chỉnh đã thực hiện**: work trả kết quả best-first, getter heap là bản sao; Size và file output lấy từ kết quả thực.
  Vị trí bản sửa: `src/algo/WUIP/WUIPAlgo.java; src/algo/TNUFPHybridSolution/TNUFPHybridAlgo.java; src/utils/ExperimentRunner.java`.

## C15 — Gọi work lần hai giữ trạng thái lần trước

**[Vị trí / Vấn đề]**: WUIPAlgo.java:19–20,26; TNUFPHybridAlgo.java:17–18,24.

- **Minh chứng gốc**: minSup và topKUFPs là fields, không được reset đầu work; chỉ MemoryLogger được reset.
- **Phân tích rủi ro**: ApiProbe chạy hai lần cùng một instance với dữ liệu {1:0.9,2:0.8}, k=3. Lần đầu heap chứa {1,2},{1},{2}; lần hai chứa {2},{1},{1}, bị lặp singleton và mất cặp. Các Main hiện tạo instance mới ở mỗi run, nên không gán lỗi này cho vòng lặp Main hiện tại; lỗi phát sinh khi caller tái sử dụng object.
- **Điều chỉnh đã thực hiện**: Tạo trạng thái solver/top-k/stats mới mỗi work; kiểm thử lại cùng instance, đổi k và database rỗng.
  Vị trí bản sửa: `src/algo/WUIP/WUIPAlgo.java; src/algo/TNUFPHybridSolution/TNUFPHybridAlgo.java; src/algo/TUFP/TUFPAlgo.java`.

## C16 — Memory báo cáo là max heap đã lấy mẫu, không phải Average Memory theo thời gian

**[Vị trí / Vấn đề]**: tools/MemoryLogger.java:72–85; MainWUIP.java:38–50; MainTUFP.java:33–45; TUFPAlgo.java:69,95,136.

- **Minh chứng gốc**: currentMemory = (totalMemory() - freeMemory()) / 1024d / 1024d; if (currentMemory > maxMemory) maxMemory = currentMemory; avgMem += getMaxMemory();
- **Phân tích rủi ro**: Main lấy trung bình các giá trị max được gọi ở một số điểm, đơn vị theo phép chia là MiB. Không có bộ lấy mẫu RSS hay trung bình theo thời gian. ApiProbe với TUFP k=1 trả memory=0.0 vì loop mining không vào checkMemory, dù đã tạo dữ liệu/lists. Reset TUFP còn nằm sau bước tạo singleton lists. Có thể làm so sánh bộ nhớ lệch hoặc bỏ qua đỉnh tạm thời.
- **Điều chỉnh đã thực hiện**: Dùng một giao thức cho cả ba phương pháp: start/end, checkpoint và sampler; gọi tên SampledMaxUsedHeapMiB. AvgMemory là mean của maxima qua các lần lặp, không phải time-average hay RSS.
  Vị trí bản sửa: `src/utils/ExperimentRunner.java; src/tools/MemoryLogger.java`.

## C17 — Chưa thể xác nhận các số liệu của bài báo từ ZIP này

**[Vị trí / Vấn đề]**: Toàn bộ archive; utils/Constant.java:11–20; các Main classes.

- **Minh chứng gốc**: Archive có 34 file Java; numberOfRuns=1, inputFileName=foodmart, KValue=900, datasetBasePath trỏ đường dẫn /Users/mac/...; không có sáu UDB của bài, scripts gán xác suất, logs gốc hay script đối chiếu quality được nhắc tới trong comment.
- **Phân tích rủi ro**: Biên dịch được và có counterexamples không cho biết từng kết quả runtime/memory/Jaccard cũ đã thay đổi bao nhiêu. Không có manifest gắn source hash với mỗi hình. Các số đo hiện tại chưa được tái lập trong lượt kiểm toán này.
- **Điều chỉnh đã thực hiện**: Không đủ minh chứng để đánh giá mục này. Vui lòng cung cấp thêm thông tin về sáu UDB, quy trình gán xác suất/seed, cấu hình máy/JVM và log thí nghiệm gốc. Bổ sung CLI tái lập nhưng chưa thay thế bằng số liệu giả định.
  Vị trí bản sửa: `README.md; verification/summary.json`.

## Phần bổ sung ngoài C01–C17

**[Vị trí / Vấn đề]**: prototype algo.UP và các cache/list được dùng qua API.

- **Minh chứng gốc**: `proba /= 10;`, `UPTree upTree = new UPTree();` nhưng tiếp theo gọi `currentTree.addTransaction(...)`; `child.setM(upNode.getM())` chia sẻ cùng mảng M. Các getter trả list mutable trong khi cache không được vô hiệu hóa trên mọi đường sửa đổi.
- **Phân tích rủi ro**: thay xác suất hợp lệ, sửa nhầm cây nguồn, chia sẻ trạng thái hoặc đọc lại support cũ sau khi list thay đổi.
- **Điều chỉnh đã thực hiện**: prototype giữ p chuẩn, dùng maps/rank, tạo và trả cây conditional mới, không chia sẻ M, dựng lại header không nhân đôi. TID/UP entries được làm bất biến, Hybrid list không giữ cache độc lập với các mutations; Itemset vô hiệu hóa tie-key cả qua subList; Output không sửa Itemset của caller. Prototype vẫn nằm ngoài chứng nhận oracle của ba miner.
