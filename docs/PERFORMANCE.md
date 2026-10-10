# Desktop performance — 10 October 2026

Single controlled Chrome run, same editor with original vs optimized pagination. Milliseconds; these are not Android IME latency or device RAM measurements. Small cases can regress and timings are noisy. Earlier contention-affected runs are retained in validation/audit.

|Pages|Cold before|Cold after|One edit before|One edit after|Command P95 before/after|
|---|---|---|---|---|---|
|1|51.2|46.5|3.4|4.6|26.0/16.0|
|5|12.8|19.1|14.1|2.4|1.5/2.3|
|20|46.8|33.8|50.2|3.2|1.5/1.7|
|50|109.6|65.4|113.0|3.7|1.7/2.1|
|100|235.3|117.4|208.7|7.0|3.1/4.1|

Final vector export run (text fixture; image-heavy documents can differ):

|Pages|Total ms|Vector ms|PDF bytes|Maximum long task ms|
|---|---|---|---|---|
|1|192.9|139.6|40034|70|
|5|191.8|182.0|55391|0|
|20|759.9|654.7|127807|81|
|50|1576.3|1520.0|244428|143|
|100|2774.5|2233.4|443229|496|

Caching unchanged blocks and yielding during pagination improve larger fixtures. Long tasks remain (including the reported 496 ms task at 100 pages), so low-end hardware smoothness is not established. No virtualization or device GC/RSS claim is made. The four-suggestion follow-up changes photo encoding, boot transport and native save work; it was not used to claim any additional measured typing speedup.
