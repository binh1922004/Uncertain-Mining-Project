# Báo cáo kiểm toán và nghiệm thu gói TopK_Uncertain_PC_Reporting

## Phạm vi và kết luận

Nguồn: TopK_Uncertain_3Algorithms_v2.zip; bản thảo NTUFP/TidNTUFP và các kịch bản E1–E4 đã đề xuất. ZIP có cả TUFP trong mã nguồn mặc dù tên ghi 3 algorithms. Không có đủ sáu input `.hui` thực nghiệm gốc. Gói tích hợp mới phù hợp để chuyển sang thực nghiệm chính thức sau khi kiểm tra hash input và môi trường; không phải bằng chứng về hiệu năng trên dữ liệu thực.

## Kiểm toán thiết kế

1. NTUFP v2 giữ đúng mục tiêu top-k ExpSuppCap; cận nhánh min(sigma(anchor),|T_X|) an toàn vì ExpSuppCap(Y) <= sigma(anchor(Y)) <= sigma(anchor(X)) và ExpSuppCap(Y) <= |T_Y| <= |T_X| đối với descendant chuẩn. Không cắt nhánh chỉ theo cap của parent.
2. TidNTUFP v2 tính exact expected support bằng xác suất joint và xác suất của item mở rộng. Cận cap và remaining-support stopping được so sánh nghiêm ngặt với theta để không mất đồng hạng. Kiểm thử fixed-point, decimal fallback, mode array/bitmap/adaptive. v2 thay đổi biểu diễn và mã thực hiện so với v1, do đó không nhập lại số đo v1.
3. TUFP dùng CUP list, conditional max bound, exact TID intersection; giữ nguyên source thuật toán đã được cung cấp. ITUFP dùng UP/IMCUP list, PUFP, ppf và PUT, đúng phạm vi one-shot; giữ nguyên cơ chế lưu và cập nhật danh sách. Không đưa fixed-point hay các kỹ thuật riêng TidNTUFP vào hai baseline. Bản Java TUFP/ITUFP là bản tái hiện/hiệu chỉnh, chưa xác nhận tương đương implementation gốc của tác giả.
4. Bổ sung ablation capEnabled cho TidNTUFP, mặc định true; OFF chỉ vô hiệu hóa phép reject theo cap (metadata join, cấu trúc, order, số học, các pruning khác giữ nguyên). Cận và stopping độc lập bật/tắt; decimal fallback không duy trì mọi flags, runner không công nhận lượt fallback cho E2/E3. E1/E4 vẫn ghi nhận backend để phân tích đầy đủ.
5. Adapter UnifiedBenchmark chung và runner Python mới, không dùng checkpoint hay thông số đường dẫn tuyệt đối của môi trường lịch sử. Cùng parser và sampler, fresh JVM; kiểm tra input/reference hash và JAR/source hash; receipt không ghi đè, resume và kiểm tra output/log hash. Runner lưu timeout, OOM, lỗi tiến trình riêng. OOM classification được rà soát mã nhưng chưa cưỡng bức OOM trên workload thật.
6. 6 exact reference lịch sử có trong gói, được khóa theo SHA-256 và input hash. Không có dữ liệu thật nên chưa thể tính lại các reference đó. E4 tự tạo reference độc lập cho từng tập con. NTUFP kiểm chứng full-scale ở mức score từng mẫu và agreement, không coi exact ExpSup reference là ground truth của surrogate ranking.

## Kết quả kiểm thử đã thực hiện

| Kiểm thử | Số lượt | Kết quả |
|---|---:|---|
| NTUFP v2, TidNTUFP decimal gốc, TUFP: oracle rational | 8.994 | 0 sai lệch |
| TidNTUFP v2 optimized: oracle rational, 3 TID modes, fallback | 9.715 | 0 sai lệch |
| TidNTUFP ablation 2 x 2 | 10.908 | 0 sai lệch |
| ITUFP Java oracle | 17.614 | 0 sai lệch |
| ITUFP independent Fraction oracle (fresh + interactive) | 11.588 | 0 sai lệch |
| Unified runner smoke | 10 | 10 completed |
| Unified runner timeout test | 10 | 10 timeout, không có mean |

Tổng các lượt kiểm chứng thuật toán trên các bộ test: 58.819. Một số trường hợp được dùng lại giữa các bộ test, không phải số dataset duy nhất. Bộ ablation kiểm tra 2.727 query-cases x 4 biến thể, có 2.690 cap rejections và 2.771 early rejections. Bộ ITUFP giữ kiểm tra published BC partial/resume và PUFP counterexample. Runner còn kiểm tra resume không chạy trùng, sai SHA-256, thay đổi giao thức, output exact bị làm sai, log bị sửa, kích thước và resume tập con E4. Xem final_selftest.log (kết quả chạy lại toàn bộ sau tích hợp), các log chuyên biệt, mã test và build_manifest.json.

## Quyết định thực nghiệm và lưu ý

E1=360 (30 cấu hình dataset–k × 4 thuật toán × 3), E2=48 (36 ablation + 12 array/bitmap), E3=36 (6 dataset × 2 k × 3 thuật toán exact), E4=72 (18 lượt ở 100% chạy lại), tổng 516. Đây là lịch dự kiến, không phải số lượt đã hoàn thành. Thiết lập mặc định bám cấu hình cũ (128m/4g, 900s, 5ms, 0 warmup); dùng đúng một máy cho toàn bộ so sánh mới. Chạy trước smoke; nếu thiếu RAM/timeout có thể điều chỉnh giao thức cho tất cả phương pháp và mở thư mục kết quả mới. Không chuyển kết quả thiếu lượt thành mean; không khẳng định cải thiện speedup khi chưa có phép đo.

Các thay đổi chưa được thực hiện: không sửa bản thảo, không tạo dữ liệu giả thay dữ liệu thật, không công bố kết quả E1–E4. Hệ điều hành kiểm thử là Linux; runner thiết kế đa nền tảng, chưa thử trực tiếp trên Windows/macOS. Không có mã ITUFP/TUFP chính thức của tác giả để xác nhận source-level fidelity tuyệt đối.

## Bổ sung phiên bản Reporting

- So với PC Final, bổ sung 12 lượt Mushrooms k=900, 6 lượt array/bitmap T10I4D100K và 18 lượt workload k=300. Không sửa Java/JAR. Lịch xen kẽ lượt lặp trong block dataset–k–scale.
- Kiểm soát tiến trình tạo independent reference bằng deadline riêng, bảo toàn prefix 700 mẫu lịch sử khi mở rộng top-900; cache input/reference và hash. Lượt chưa thể chuẩn bị reference không được đánh dấu là số đo hợp lệ.
- Xuất bảng đủ kế hoạch, số đo thô, kiểm chứng, E1–E4, speedup hợp lệ, contrasts, cap filtering, thống kê dữ liệu, cùng cờ readiness. Không điền 0 cho số đo thiếu; chỉ kết luận trên nhóm có đủ lượt hợp lệ và khớp output.
- Chạy lại `verify_runner.py`: PASS 10 smoke, 10 timeout, resume, hash, protocol, output và subset. Chạy `verify_publication.py`: PASS 516 lượt kế hoạch rỗng, 10 smoke, mở rộng reference synthetic top-700→900 đúng prefix, timeout reference, và 45 JVM synthetic bao phủ E1–E4. Xem `provenance/runner_reporting_tests.log` và `provenance/publication_tests.log`. Các log kiểm thử thuật toán cũ được giữ lại theo nguồn gốc; không tuyên bố đã chạy lại 58.819 truy vấn trong lần sửa reporting.
- Chưa có 6 input thật; chưa tính reference Mushrooms top-900 hay các subset trên dữ liệu thật, chưa đo 516 lượt, chưa kiểm tra hiệu năng trên máy người dùng. Không sửa số liệu hoặc bản thảo.
