# Top-K Uncertain — PC Reporting (516 lượt dự kiến)

Gói chạy thực nghiệm thống nhất cho NTUFP v2, TidNTUFP v2, TUFP và ITUFP. Tách kết quả mới khỏi số đo lịch sử. Không bổ sung kỹ thuật tối ưu vào baseline TUFP/ITUFP.

## 1. Yêu cầu

- JDK 21 (64-bit) hoặc mới hơn, `java -version` hoạt động trong Terminal; cần `javac` nếu biên dịch lại hoặc chạy toàn bộ self-test.
- Python 3.10+ và NumPy. Runner không cần Maven/Gradle, GPU hay Internet sau khi cài NumPy.
- Nên có RAM 16 GiB trở lên; JVM mặc định tối đa 4 GiB, chỉ chạy tuần tự một JVM. Cần thêm RAM cho hệ điều hành và oracle Python. Không bảo đảm mọi cấu hình lớn sẽ hoàn thành trong giới hạn này.
- Windows/macOS/Linux: dùng Terminal hoặc PowerShell, chuyển vào thư mục vừa giải nén. Trên Windows có thể thay `python3` bằng `py -3` (hoặc `python`). Tránh đường dẫn giải nén quá dài.

## 2. Cài đặt và kiểm tra nhanh

```bash
python3 -m pip install -r requirements.txt
java -version
python3 run.py --smoke --out smoke_results --xmx 512m
```

Kết quả smoke mong đợi: 10/10 lượt `completed`. Không dùng số đo smoke làm số liệu bài báo. File `topk-benchmark.jar` đã được build bằng JDK 21; không cần build lại để chạy.

Kiểm tra riêng cơ chế lập bảng, reference mở rộng và 45 lượt JVM giả lập: `python3 verify_publication.py`.

Nếu muốn kiểm chứng sâu toàn bộ thuật toán và runner:

```bash
python3 selftest.py
```

Kết quả mong đợi: `ALL TESTS PASSED`. Script này biên dịch lại mã nguồn phục vụ kiểm thử, không thay đổi JAR benchmark. Nếu chủ động sửa mã nguồn, chạy `python3 build.py` để tạo lại JAR và manifest; sau đó phải dùng thư mục kết quả mới.

## 3. Chuẩn bị dữ liệu thật

Chép đúng 6 file CSDL bất định `.hui` đã sử dụng trong thực nghiệm gốc vào thư mục `data/`:

```
data/chess.hui
data/mushrooms.hui
data/T10I4D100K.hui
data/pumsb_star.hui
data/pumsb.hui
data/connect.hui
```

Các ZIP được cung cấp không chứa 6 file này. Không tải CSDL chắc chắn rồi tự gán lại xác suất nếu muốn dùng exact references lịch sử. File `data/EXPECTED_SHA256.txt` ghi hash cần khớp byte-for-byte. Runner kiểm tra SHA-256 của input và reference trước khi chạy. Nếu khác hash, dừng thay vì so sánh sai ground truth. Dữ liệu `.hui`: dòng đầu `n m`, sau đó mỗi dòng `TID item probability`; miền xác suất của dữ liệu thực nghiệm là các bội số dương của 0,1. Reference cho E4 yêu cầu các dòng được sắp theo TID. Nếu dữ liệu đặt ở thư mục khác, dùng `--data-dir DUONG_DAN`.

## 4. Chạy chính thức

Xem kế hoạch mà không cần dataset:

```bash
python3 run.py --suite all --dry-run
```

Chạy E1 trước, sau đó lần lượt các kịch bản bổ sung:

```bash
python3 run.py --suite E1 --out results
python3 run.py --suite E2 --out results
python3 run.py --suite E3 --out results
python3 run.py --suite E4 --out results
```

Hoặc chạy tất cả bằng một lệnh:

```bash
python3 run.py --suite all --out results
```

Chạy riêng một dataset để pilot (đây vẫn là lượt đo chính thức nếu môi trường không đổi):

```bash
python3 run.py --suite E1 --datasets chess --out results
```

Mặc định: Xms128m, Xmx4g, 900 giây wall-clock mỗi JVM, 5ms lấy mẫu heap, 0 warm-up, mỗi lần chạy là JVM mới. Có thể thay bằng `--xmx 6g --deadline 1800` nếu RAM đủ và muốn thiết kế giao thức khác; phải giữ nguyên tham số cho mọi thuật toán và dùng thư mục kết quả mới. Không chạy hai runner cùng lúc trên một máy khi đo. Không so sánh trực tiếp thời gian trên máy khác hoặc phiên bản khác.

## 5. Kịch bản

| Suite | Thiết kế | Lượt chạy tối đa |
|---|---|---:|
| E1 | 6 datasets, k=100/300/500/700/900, đủ cả 5 giá trị k; 30 cấu hình x 4 thuật toán x 3 | 360 |
| E2 | 3 datasets (Chess, Connect, T10I4D100K), k=300, TidNTUFP ExpSuppCap ON/OFF x remaining-support stopping ON/OFF x 3; thêm array/bitmap tại Connect và T10I4D100K, k=300, mỗi kiểu 3 lượt | 48 |
| E3 | 6 datasets, k=100 và 300, ba thuật toán exact; 1 lượt chẩn đoán mỗi cấu hình | 36 |
| E4 | Connect và T10I4D100K, 25/50/75/100% số giao dịch, k=100, ba thuật toán exact x 3 | 72 |
| Tổng | E4 100% chạy lại để giữ một series riêng, không tái sử dụng E1 | 516 |

E4: lấy mẫu lồng nhau từ một hoán vị TID với seed 20261126; kích thước floor(n * fraction), đánh lại TID liên tiếp theo thứ tự gốc và giữ nguyên xác suất. Tự xây independent exact reference cho 6 tập con (<100%); 100% dùng reference gốc. Reference generation không nằm trong thời gian mining hoặc deadline Java. Mushrooms k=900 được mở rộng độc lập từ input gốc và phải khớp chính xác prefix 700 mẫu của reference lịch sử. Mỗi tiến trình tạo reference có giới hạn mặc định 3600 giây (`--reference-deadline`), được lưu log riêng; nếu thất bại, runner ghi `blocked_reference` cho các lượt liên quan và tiếp tục các lượt khác. Nếu bị ngắt lúc sinh reference, lần sau runner tạo lại tập con chưa có receipt. Việc tạo reference có thể tốn nhiều RAM và thời gian, độc lập với phép đo thuật toán.

E1 có NTUFP surrogate ExpSuppCap; ba thuật toán còn lại xếp hạng theo exact ExpSup. Chỉ TUFP/ITUFP/TidNTUFP là đối chứng exact trực tiếp. E2/E3/E4 thiết kế để kiểm tra phương pháp exact. Không gộp NTUFP vào kết luận speedup như thể nó giải quyết cùng hàm xếp hạng.

## 6. Giao thức đo, xử lý lỗi và kết quả

- Cùng parser, ranh giới mining time (khởi tạo cấu trúc riêng của phương pháp + mining), bộ lấy mẫu heap và output chuẩn. Parsing/kiểm tra input, ghi kết quả, kiểm tra oracle nằm ngoài mining time. Wall-clock deadline bao phủ JVM, gồm đọc/ghi file. Heap là sampled max used Java heap (MiB), không phải RSS hay bộ nhớ đỉnh tuyệt đối.
- Mọi output exact được kiểm tra đầy đủ mẫu, giá trị rational và thứ tự (score giảm, kích thước tăng, ID tăng từ điển). NTUFP: xác minh score từng mẫu được trả, thứ tự, kích thước, tính duy nhất và Jaccard/overlap với exact; không tuyên bố oracle full-scale xác nhận tính đầy đủ của top-k surrogate. Oracle vét cạn kiểm tra đầy đủ top-k surrogate trên dữ liệu nhỏ.
- Các lượt timeout/OOM/invalid_output/process_error không tính vào mean; chỉ báo cáo mean/SD/min/max ở nhóm đủ 3 lượt hợp lệ với output giống nhau. E3 là chẩn đoán, không tính mean runtime. Không gán timeout = 900 giây để tính mean. Với E2/E3, nếu TidNTUFP chuyển sang decimal fallback (không bảo toàn flags ablation/counters), runner gắn `unsupported_fallback`, không dùng số đo làm kết luận ablation/work counters.
- Cơ chế tiếp tục: Ctrl+C, chạy lại cùng lệnh. Lượt hoàn thành/timeout/OOM đã ghi receipt sẽ không bị chạy lại. Lượt gián đoạn được thử lại ở thư mục attempt mới. Nếu máy tắt đột ngột, chỉ xóa `results/RUNNING.lock` sau khi chắc chắn runner và JVM cũ đã dừng. Không sửa/xóa receipts khi muốn tiếp tục.
- `environment.json` khóa phiên bản Java, môi trường, flags, hash JAR, runner, mã reference và manifest. Nếu thay đổi, dùng thư mục `--out` mới. Không sử dụng `python -O` vì các kiểm tra bổ sung có assert.

File cần lấy sau khi chạy:

- `results/summary.csv`: kết quả theo nhóm cấu hình, completion count, time/heap mean SD min max, agreement.
- `results/runs.csv`: từng lượt, trạng thái, time, heap, work counters.
- `results/work_counters.csv`: E3.
- `results/plan.json`, `environment.json`, `runs.json`, `summary.json`: tái lập và kiểm tra cấu hình còn thiếu.
- `results/runs/<job>/attempt_.../patterns.txt`, `stdout.log`, `stderr.log`, `receipt.json`: bằng chứng từng lượt, có hash.
- `results/derived/`: E4 subset + independent exact reference và hash.

### Bảng phục vụ phân tích và viết bài

Runner tự cập nhật `results/publication/` theo từng receipt. Có thể xuất lại bằng:

```bash
python3 export_report.py --out results
```

| File | Nội dung và số hàng khi lập đủ kế hoạch |
|---|---|
| `00_readiness.json` | Trạng thái 516 lượt, số lượt chưa lên lịch, pending, blocked, timeout/OOM, lỗi, kết quả hợp lệ; cap totals kèm coverage |
| `01_coverage_all_jobs.csv` | 516 lượt dự kiến, kể cả chưa chạy |
| `02_raw_runs_all.csv` | 516 dòng kế hoạch + số đo/kiểm chứng từng lượt nếu có |
| `03_groups_all.csv` | Tổng hợp theo nhóm; không nội suy số đo thiếu |
| `04_E1_overall.csv` | 120 nhóm: 6 datasets × 5 k × 4 phương pháp |
| `05_E1_exact_pairwise.csv` | 60 đối chiếu TUFP/ITUFP với TidNTUFP; speedup và % giảm heap khi hợp lệ |
| `06_E2_ablation_and_storage.csv` | 16 nhóm ablation/storage |
| `06b_E2_contrasts.csv` | Các đối chiếu ON/OFF riêng rẽ và phối hợp, array/bitmap/adaptive |
| `07_E3_workload.csv` | 36 lượt chẩn đoán, giữ counter chung và counter riêng |
| `08_E4_scalability.csv` | 24 nhóm scalability |
| `09_E1_cap_filtering.csv` | 30 nhóm TidNTUFP; cap checks/rejections và tỉ lệ khi bộ đếm nhất quán |
| `10_E4_subset_provenance.csv` | Quy mô, thống kê và hash input/reference E4 đã chuẩn bị |
| `11_dataset_statistics.csv` | Thống kê sáu input thật sau khi hash và parser kiểm tra |

`full_protocol_terminal` cho biết toàn bộ kế hoạch đã có receipt kết thúc; `full_protocol_all_valid` đòi hỏi mọi nhóm có đủ lượt hợp lệ; `full_protocol_outcomes_ready` chấp nhận timeout/OOM là kết cục thực nghiệm nhưng không chấp nhận lỗi tiến trình, sai output hay fallback không hỗ trợ. Không hiểu các cờ này như bảo đảm thuật toán thắng baseline; smoke chỉ kiểm thử trên fixture. Các bảng chỉ cung cấp mean/SD/min/max và speedup cho nhóm đáp ứng điều kiện. Ô trống nghĩa là chưa có hoặc không đủ điều kiện báo cáo, không phải 0. Không diễn giải counter khác định nghĩa thành một đại lượng chung. Không dùng số đo fixture/smoke cho bài báo.

Sau khi chạy xong, nén cả thư mục `results/` và gửi lại để kiểm toán kết quả, lập bảng/hình và cập nhật bài báo. Không trộn số liệu v1 cũ với v2 mới.

## 7. Giải thích bộ đếm

`eligiblePairs`: cặp sibling còn qua kiểm tra support của parents; `exactCandidateJoins`: ứng viên vào pha exact join/materialization (kể cả dừng sớm); `probabilityMultiplications`: số phép nhân xác suất joint (không gồm phép tính cap), ba chỉ số được định nghĩa chung. `capChecks`, `capRejections`, `pufpRejections`, `ppfStops`, `earlyRejections` phản ánh kỹ thuật riêng, không đánh đồng mọi loại pruning. `tidPairMerges` của TidNTUFP đếm merge theo block; của TUFP đếm join list, không so sánh trực tiếp. `metadataPairs` là số cặp node, không phải số TID. Các bộ đếm nhẹ có mặt trong mã của cả benchmark và diagnostic; E3 chạy riêng để phân tích workload. Với ITUFP one-shot, `resumedLists`/`reusedComplete` không phải chỉ số đánh giá interactive mining.

## 8. Tính trung thành baseline và giới hạn

ITUFP là independent Java reconstruction của Davashi (2023) gồm UP-List, IMCUP-List, PUFP, ppf, PUT; TUFP là corrected CUP-list implementation được cung cấp. Không thay đổi cấu trúc, phép pruning hay chiến lược tìm kiếm của chúng trong bản tích hợp; chỉ bổ sung counter và adapter. ITUFP giữ đầy đủ cache của phương pháp gốc kể cả one-shot; không ép chạy interactive. Baseline dùng BigDecimal, đề xuất v2 dùng tenths-long và fallback; chênh lệch triển khai ảnh hưởng runtime và heap. Vì vậy chỉ kết luận về hiệu năng tổng thể triển khai, kết hợp E2/E3 để diễn giải cơ chế; không quy toàn bộ speedup cho một pruning. Không khẳng định Java baseline giống mã chính thức của tác giả.

Gói đã vượt qua kiểm thử synthetic và smoke thực tế trên Linux/JDK 21. Chưa có dữ liệu benchmark gốc nên chưa thể xác nhận runtime/heap, 516 lượt chính thức, hoặc test thực thi trên chính máy người dùng. Xem `AUDIT.md` và `provenance/final_selftest.log`.
