# Load Test


| Test Case | Module | CPU (m) | Memory (Mi) | Success rate (%) |
| --------- | ------ | --- | ------ | ------------ |
| No load | Server | 5 | 200 | | 
|         | Client | 7 | 164 | |
|         | Authorize | 6 | 175 | |
|         | Kafka | 24 | 450 | |
| 1 rps   | Server | 5 | 200 | 100 | 
|         | Client | 7 | 164 | |
|         | Authorize | 6 | 175 | |
|         | Kafka | 24 | 450 | |
| 10 rps  | Server | 5 | 200 | 100 | 
|         | Client | 7 | 164 | |
|         | Authorize | 6 | 175 | |
|         | Kafka | 24 | 450 | |
| 100 rps | Server | 71 | 200 | 100 | 
|         | Client | 62 | 177 | |
|         | Authorize | 25 | 175 | |
|         | Kafka | 69 | 450 | |