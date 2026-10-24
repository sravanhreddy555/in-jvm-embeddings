# Benchmark results (2026-10-24)

| Machine | |
|---|---|
| CPU | Intel(R) Core(TM) i7-8850H CPU @ 2.60GHz |
| Logical processors | 12 |
| RAM | 31.7 GiB |
| OS | Windows 11 10.0 |
| JVM | OpenJDK 64-Bit Server VM 21.0.12.1+1-LTS |
| ONNX Runtime | 1.31.0 |
| Power | AC |

## precision@5

41 labeled queries from bench/data/queries.json. Best possible mean: 0.927 (a query with 3 relevant postings scores at most 3/5).

| Setup | Mean | Skill queries | Role queries |
|---|---:|---:|---:|
| local, full posting | 0.551 | 0.460 | 0.638 |
| local, requirements-only | 0.820 | 0.760 | 0.876 |

## Per query

| Query | Kind | Relevant | local, full posting | local, requirements-only |
|---|---|---:|---:|---:|
| someone to keep production Kubernetes clusters reliable and respond to outages | skill | 5 | 0.0 | 0.8 |
| build and schedule data pipelines with Apache Airflow | skill | 4 | 0.4 | 0.8 |
| train and deploy deep learning models with PyTorch | skill | 3 | 0.0 | 0.6 |
| iPhone app developer who uses SwiftUI | skill | 4 | 0.6 | 0.8 |
| Android apps written in Kotlin with Jetpack Compose | skill | 4 | 0.8 | 0.8 |
| web UI work with Next.js | skill | 4 | 0.4 | 0.8 |
| experienced Go programmer building gRPC services | skill | 4 | 0.6 | 0.8 |
| Java Spring Boot microservices with Kafka event streaming | skill | 7 | 1.0 | 0.8 |
| Python web backend using Django | skill | 4 | 0.4 | 0.8 |
| infrastructure as code with Terraform | skill | 5 | 0.2 | 0.6 |
| architect for Microsoft Azure cloud migrations | skill | 5 | 0.8 | 1.0 |
| ethical hacker to perform penetration tests | skill | 5 | 0.4 | 1.0 |
| end-to-end browser test automation with Playwright | skill | 3 | 0.4 | 0.6 |
| firmware for microcontrollers | skill | 3 | 0.2 | 0.6 |
| Unity game programmer | skill | 5 | 0.4 | 0.8 |
| keep databases backed up, replicated and highly available | role | 7 | 0.6 | 1.0 |
| own the product roadmap and prioritise features | skill | 7 | 0.8 | 0.8 |
| designer who builds and maintains a design system in Figma | skill | 4 | 0.4 | 0.6 |
| write developer documentation and API guides | role | 6 | 1.0 | 1.0 |
| lead and grow a team of software engineers | role | 7 | 0.6 | 1.0 |
| technical pre-sales demos for enterprise prospects | role | 6 | 0.4 | 0.8 |
| design and operate corporate networks, routers and firewalls | role | 8 | 1.0 | 1.0 |
| Salesforce Apex and Lightning developer | role | 6 | 0.2 | 0.8 |
| financial crime prevention and regulatory compliance | role | 7 | 1.0 | 1.0 |
| design A/B tests and analyse experiment results | skill | 7 | 0.4 | 0.6 |
| build Tableau dashboards for business stakeholders | skill | 7 | 0.8 | 1.0 |
| search engine optimisation and content strategy | skill | 3 | 0.2 | 0.6 |
| source and hire software engineers | role | 7 | 0.0 | 0.0 |
| smart contracts on Ethereum | role | 6 | 0.8 | 1.0 |
| build native apps for Apple devices | role | 8 | 1.0 | 1.0 |
| video game development | role | 8 | 0.4 | 1.0 |
| user experience research and interface design | role | 8 | 0.6 | 1.0 |
| memory-safe low-latency systems programming | role | 7 | 0.4 | 1.0 |
| care for patients on hospital wards | role | 8 | 0.8 | 1.0 |
| bookkeeping and preparing financial statements for the audit | role | 8 | 1.0 | 1.0 |
| plan budgets and explain variances to leadership | role | 8 | 0.2 | 1.0 |
| design physical product parts and take them to manufacturing | role | 7 | 0.4 | 0.8 |
| warehouse and supply chain operations | role | 6 | 1.0 | 1.0 |
| help business customers succeed after they sign the contract | role | 8 | 0.2 | 0.6 |
| create visual assets and brand identity for marketing | role | 7 | 0.8 | 0.6 |
| keep office laptops, user accounts and the helpdesk running | role | 8 | 1.0 | 0.8 |
