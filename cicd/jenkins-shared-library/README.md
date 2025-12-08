# Jenkins Shared Library - Production Grade

[![Build Status](https://jenkins.example.com/buildStatus/icon?job=shared-library)](https://jenkins.example.com/job/shared-library/)
[![Quality Gate Status](https://sonarqube.example.com/api/project_badges/measure?project=shared-library&metric=alert_status)](https://sonarqube.example.com/dashboard?id=shared-library)

Enterprise-grade Jenkins shared library for ISO 8583 payment processing CI/CD pipelines.

## Features

- ✅ **Security First**: Built-in vulnerability scanning, secret detection
- ✅ **Quality Gates**: SonarQube integration with quality thresholds
- ✅ **Parallel Execution**: Optimized build times with parallel stages
- ✅ **Error Handling**: Comprehensive error handling and rollback capabilities
- ✅ **Notifications**: Slack/Teams integration for pipeline status
- ✅ **Compliance**: Audit trails and compliance reporting
- ✅ **Multi-Environment**: Support for dev/staging/production deployments

## Quick Start

### 1. Jenkins Configuration
```groovy
// Global Pipeline Libraries
Name: iso8583-pipeline-lib
Default version: main
Load implicitly: true
Allow default version to be overridden: true
```

### 2. Basic Usage
```groovy
@Library('iso8583-pipeline-lib') _

ciPipeline([
    buildType: 'maven',
    build: [workDir: 'source'],
    test: [coverage: true, parallel: true],
    security: [enabled: true],
    sonar: [enabled: true, server: 'SonarQube'],
    deploy: [
        type: 'k8s',
        namespace: 'production',
        manifestPath: 'k8s/manifest/'
    ],
    notifications: [
        slack: [channel: '#deployments']
    ]
])
```

## Pipeline Steps

| Step | Description | Features |
|------|-------------|----------|
| `buildMaven` | Maven compilation | JVM optimization, profile support |
| `buildNode` | Node.js build | Security audit, dependency check |
| `testMaven` | Maven testing | Coverage, parallel execution |
| `testNode` | Node.js testing | Coverage reports, linting |
| `packageMaven` | Artifact creation | Security scanning, fingerprinting |
| `deployDocker` | Container deployment | Multi-stage builds, vulnerability scan |
| `deployK8s` | Kubernetes deployment | Validation, rollback, health checks |
| `sonarScan` | Code quality analysis | Quality gates, technical debt |
| `notifySlack` | Team notifications | Status updates, failure alerts |

## Configuration Examples

### Maven Multi-Module Project
```groovy
ciPipeline([
    buildType: 'maven',
    build: [
        workDir: 'source',
        profiles: ['production'],
        jvmArgs: '-Xmx4g -XX:+UseG1GC'
    ],
    test: [
        workDir: 'source',
        coverage: true,
        parallel: true,
        testPattern: '**/*Test.java'
    ],
    integrationTest: [
        enabled: true,
        testPattern: '**/*IT.java'
    ],
    package: [
        workDir: 'source',
        securityScan: true
    ],
    deploy: [
        type: 'docker',
        imageName: 'iso8583/payment-processor',
        registry: 'registry.company.com',
        securityScan: true,
        push: true
    ]
])
```

### Node.js Application
```groovy
ciPipeline([
    buildType: 'node',
    build: [
        workDir: 'source/console',
        nodeVersion: '20',
        command: 'npm run build:prod'
    ],
    test: [
        workDir: 'source/console',
        coverage: true,
        command: 'npm run test:ci'
    ],
    deploy: [
        type: 'k8s',
        namespace: 'frontend',
        manifestPath: 'k8s/manifest/console-deployment.yaml',
        timeout: '600s'
    ]
])
```

## Security Features

- **Secret Scanning**: Prevents credential leaks
- **Dependency Vulnerability Scanning**: OWASP dependency check
- **Container Security**: Trivy image scanning
- **License Compliance**: Automated license verification
- **SAST Integration**: Static application security testing

## Monitoring & Observability

- **Build Metrics**: Duration, success rate, failure analysis
- **Quality Metrics**: Code coverage, technical debt
- **Security Metrics**: Vulnerability trends, compliance status
- **Deployment Metrics**: Frequency, lead time, MTTR

## Best Practices

1. **Branch Strategy**: Use GitFlow with protected main branch
2. **Quality Gates**: Enforce minimum 80% code coverage
3. **Security**: Scan all dependencies and container images
4. **Notifications**: Configure alerts for failures and deployments
5. **Rollback**: Always test rollback procedures

## Troubleshooting

### Common Issues

**Build Failures**
```bash
# Check Maven dependencies
mvn dependency:tree

# Verify Docker build context
docker build --no-cache .

# Validate Kubernetes manifests
kubectl apply --dry-run=client -f manifest.yaml
```

**Quality Gate Failures**
- Review SonarQube dashboard for specific issues
- Check code coverage reports
- Address security vulnerabilities

## Support

- **Documentation**: [Wiki](https://wiki.company.com/jenkins-library)
- **Issues**: [JIRA](https://jira.company.com/projects/CICD)
- **Slack**: #devops-support

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/new-step`
3. Add tests for new functionality
4. Submit pull request with detailed description

## License

Internal use only - Company Proprietary