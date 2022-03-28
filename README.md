[![GitHub Actions](https://github.com/HuygensING/hyper-collate/workflows/tests/badge.svg)](https://github.com/brambg/annorepo/actions)
![Snyk Vulnerabilities for GitHub Repo](https://img.shields.io/snyk/vulnerabilities/github/brambg/annorepo)
![GitHub language count](https://img.shields.io/github/languages/count/brambg/annorepo)
[![Project Status: WIP – Initial development is in progress, but there has not yet been a stable, usable release suitable for the public.](https://www.repostatus.org/badges/latest/wip.svg)](https://www.repostatus.org/#wip)
![Swagger Validator](https://img.shields.io/swagger/valid/3.0?specUrl=https%3A%2F%2Fraw.githubusercontent.com%2Fbrambg%2Fannorepo%2Fmain%2Fdocs%2Fswagger.json)

# AnnoRepo

A repository for [W3C Web Annotations](https://www.w3.org/TR/annotation-model/), implementing
the [W3C Web Annotation Protocol](https://www.w3.org/TR/annotation-protocol/).

Inspired by [elucidate](https://github.com/dlcs/elucidate-server).

How to start the AnnoRepo application
---

1. Run `mvn clean install` to build your application
1. Start application with `java -jar target/annorepo-0.1-SNAPSHOT.jar server config.yml`
1. To check that your application is running enter url `http://localhost:8080`

Health Check
---

To see your application's health enter url `http://localhost:8081/healthcheck`
