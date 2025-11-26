# AWS Cloud Deployment Architecture

## Multi-EKS Deployment with Network Isolation

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              AWS CLOUD INFRASTRUCTURE                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────┐     │
│  │         BANK SIDE (CLIENT)      │    │    NETWORK SCHEME (SERVER)      │     │
│  │         Region: us-east-1       │    │      Region: us-west-2          │     │
│  └─────────────────────────────────┘    └─────────────────────────────────┘     │
│                    │                                      │                     │
│                    ▼                                      ▼                     │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────┐     │
│  │        VPC: 10.0.0.0/16         │    │        VPC: 10.1.0.0/16         │     │
│  ├─────────────────────────────────┤    ├─────────────────────────────────┤     │
│  │ ┌─────────────────────────────┐ │    │ ┌─────────────────────────────┐ │     │
│  │ │     DATA NETWORK TIER       │ │    │ │     DATA NETWORK TIER       │ │     │
│  │ │   Subnet: 10.0.1.0/24       │ │    │ │   Subnet: 10.1.1.0/24       │ │     │
│  │ ├─────────────────────────────┤ │    │ ├─────────────────────────────┤ │     │
│  │ │ ┌─────────────────────────┐ │ │    │ │ ┌─────────────────────────┐ │ │     │
│  │ │ │      EKS CLUSTER        │ │ │    │ │ │      EKS CLUSTER        │ │ │     │
│  │ │ │    bank-iso8583-eks     │ │ │    │ │ │   scheme-iso8583-eks    │ │ │     │
│  │ │ ├─────────────────────────┤ │ │    │ │ ├─────────────────────────┤ │ │     │
│  │ │ │ Client Pods             │ │ │    │ │ │ Server Pods             │ │ │     │
│  │ │ │ ┌─────────────────────┐ │ │ │    │ │ │ ┌─────────────────────┐ │ │ │     │
│  │ │ │ │ Client App          │ │ │ │    │ │ │ │ Server App          │ │ │ │     │
│  │ │ │ │ Client Controller   │ │ │ │    │ │ │ │ Server Controller   │ │ │ │     │
│  │ │ │ │ (Sidecar)           │ │ │ │    │ │ │ │ (Sidecar)           │ │ │ │     │
│  │ │ │ └─────────────────────┘ │ │ │    │ │ │ └─────────────────────┘ │ │ │     │
│  │ │ │ Console/Authorize Pods  │ │ │    │ │ │ Simulator Pods          │ │ │     │
│  │ │ └─────────────────────────┘ │ │    │ │ └─────────────────────────┘ │ │     │
│  │ └─────────────────────────────┘ │    │ └─────────────────────────────┘ │     │
│  │                                 │    │                                 │     │
│  │ ┌─────────────────────────────┐ │    │ ┌─────────────────────────────┐ │     │
│  │ │   CONTROL NETWORK TIER      │ │    │ │   CONTROL NETWORK TIER      │ │     │
│  │ │   Subnet: 10.0.2.0/24       │ │    │ │   Subnet: 10.1.2.0/24       │ │     │
│  │ ├─────────────────────────────┤ │    │ ├─────────────────────────────┤ │     │
│  │ │ ┌─────────────────────────┐ │ │    │ │ ┌─────────────────────────┐ │ │     │
│  │ │ │   CONTROL EKS CLUSTER   │ │ │    │ │ │   CONTROL EKS CLUSTER   │ │ │     │
│  │ │ │  bank-control-eks       │ │ │    │ │ │  scheme-control-eks     │ │ │     │
│  │ │ ├─────────────────────────┤ │ │    │ │ ├─────────────────────────┤ │ │     │
│  │ │ │ Central Controller      │ │ │    │ │ │ Central Controller      │ │ │     │
│  │ │ │ Network Monitor         │ │ │    │ │ │ Network Monitor         │ │ │     │
│  │ │ │ Health Monitor          │ │ │    │ │ │ Health Monitor          │ │ │     │
│  │ │ │ Connection Registry     │ │ │    │ │ │ Connection Registry     │ │ │     │
│  │ │ └─────────────────────────┘ │ │    │ │ └─────────────────────────┘ │ │     │
│  │ └─────────────────────────────┘ │    │ └─────────────────────────────┘ │     │
│  └─────────────────────────────────┘    └─────────────────────────────────┘     │
│                    │                                      │                     │
│                    └──────────────────┬───────────────────┘                     │
│                                       │                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────┤
│  │                    INTER-REGION CONNECTIVITY                                │
│  ├─────────────────────────────────────────────────────────────────────────────┤
│  │ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │ │ VPC Peering     │  │ Transit Gateway │  │ Direct Connect  │              │
│  │ │ (Control Plane) │  │ (Data Plane)    │  │ (Backup Path)   │              │
│  │ └─────────────────┘  └─────────────────┘  └─────────────────┘              │
│  └─────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────────┤
│  │                         SHARED SERVICES                                     │
│  ├─────────────────────────────────────────────────────────────────────────────┤
│  │ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │ │ ElastiCache     │  │ RDS (Multi-AZ)  │  │ MSK (Kafka)     │              │
│  │ │ (Redis Cluster) │  │ (Connection     │  │ (Event Bus)     │              │
│  │ │                 │  │  Registry)      │  │                 │              │
│  │ └─────────────────┘  └─────────────────┘  └─────────────────┘              │
│  └─────────────────────────────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Network Architecture Details

### Data Network (ISO 8583 Traffic)
```yaml
# Bank Side VPC
VPC: 10.0.0.0/16
Data Subnet: 10.0.1.0/24
  - EKS Cluster: bank-iso8583-eks
  - Client Pods: ISO 8583 client applications
  - Security Groups: Port 8583 (ISO), 8081 (REST API)

# Network Scheme VPC  
VPC: 10.1.0.0/16
Data Subnet: 10.1.1.0/24
  - EKS Cluster: scheme-iso8583-eks
  - Server Pods: ISO 8583 server applications
  - Security Groups: Port 8583 (ISO), 9090 (gRPC)
```

### Control Network (Controller Communication)
```yaml
# Bank Control Network
Control Subnet: 10.0.2.0/24
  - EKS Cluster: bank-control-eks
  - Central Controller, Network Monitor
  - Security Groups: Port 6443 (K8s API), 2379 (etcd)

# Scheme Control Network
Control Subnet: 10.1.2.0/24
  - EKS Cluster: scheme-control-eks
  - Central Controller, Network Monitor
  - Security Groups: Port 6443 (K8s API), 2379 (etcd)
```

## Terraform Deployment Configuration

### VPC and Networking
```hcl
# Bank Side VPC
module "bank_vpc" {
  source = "terraform-aws-modules/vpc/aws"
  
  name = "bank-iso8583-vpc"
  cidr = "10.0.0.0/16"
  
  azs = ["us-east-1a", "us-east-1b", "us-east-1c"]
  
  # Data Network Subnets
  private_subnets = ["10.0.1.0/24", "10.0.1.64/26", "10.0.1.128/26"]
  public_subnets  = ["10.0.0.0/24", "10.0.0.64/26", "10.0.0.128/26"]
  
  # Control Network Subnets
  intra_subnets = ["10.0.2.0/24", "10.0.2.64/26", "10.0.2.128/26"]
  
  enable_nat_gateway = true
  enable_vpn_gateway = true
  
  tags = {
    Environment = "production"
    Side = "bank"
  }
}

# Network Scheme VPC
module "scheme_vpc" {
  source = "terraform-aws-modules/vpc/aws"
  
  name = "scheme-iso8583-vpc"
  cidr = "10.1.0.0/16"
  
  azs = ["us-west-2a", "us-west-2b", "us-west-2c"]
  
  # Data Network Subnets
  private_subnets = ["10.1.1.0/24", "10.1.1.64/26", "10.1.1.128/26"]
  public_subnets  = ["10.1.0.0/24", "10.1.0.64/26", "10.1.0.128/26"]
  
  # Control Network Subnets
  intra_subnets = ["10.1.2.0/24", "10.1.2.64/26", "10.1.2.128/26"]
  
  enable_nat_gateway = true
  enable_vpn_gateway = true
  
  tags = {
    Environment = "production"
    Side = "scheme"
  }
}
```

### EKS Clusters
```hcl
# Bank Data EKS Cluster
module "bank_data_eks" {
  source = "terraform-aws-modules/eks/aws"
  
  cluster_name    = "bank-iso8583-eks"
  cluster_version = "1.28"
  
  vpc_id     = module.bank_vpc.vpc_id
  subnet_ids = module.bank_vpc.private_subnets
  
  node_groups = {
    client_nodes = {
      desired_capacity = 3
      max_capacity     = 6
      min_capacity     = 2
      
      instance_types = ["m5.large"]
      
      k8s_labels = {
        role = "client"
        network = "data"
      }
    }
  }
}

# Bank Control EKS Cluster
module "bank_control_eks" {
  source = "terraform-aws-modules/eks/aws"
  
  cluster_name    = "bank-control-eks"
  cluster_version = "1.28"
  
  vpc_id     = module.bank_vpc.vpc_id
  subnet_ids = module.bank_vpc.intra_subnets
  
  node_groups = {
    control_nodes = {
      desired_capacity = 2
      max_capacity     = 3
      min_capacity     = 2
      
      instance_types = ["m5.medium"]
      
      k8s_labels = {
        role = "controller"
        network = "control"
      }
    }
  }
}
```

### Inter-Region Connectivity
```hcl
# VPC Peering for Control Plane
resource "aws_vpc_peering_connection" "control_peering" {
  vpc_id        = module.bank_vpc.vpc_id
  peer_vpc_id   = module.scheme_vpc.vpc_id
  peer_region   = "us-west-2"
  auto_accept   = false
  
  tags = {
    Name = "control-plane-peering"
    Purpose = "controller-communication"
  }
}

# Transit Gateway for Data Plane
resource "aws_ec2_transit_gateway" "iso8583_tgw" {
  description = "ISO 8583 Data Plane Transit Gateway"
  
  tags = {
    Name = "iso8583-data-tgw"
  }
}
```

## Kubernetes Deployment

### Controller Deployment (Control Network)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: central-controller
  namespace: iso8583-control
spec:
  replicas: 2
  selector:
    matchLabels:
      app: central-controller
  template:
    metadata:
      labels:
        app: central-controller
    spec:
      nodeSelector:
        network: control
      containers:
      - name: controller
        image: iso8583-controller:latest
        env:
        - name: NETWORK_MODE
          value: "control"
        - name: PEER_CONTROLLER_ENDPOINT
          value: "controller.scheme-control-eks.amazonaws.com:8080"
        ports:
        - containerPort: 8080
          name: control-api
        - containerPort: 8443
          name: secure-api
```

### Client Deployment (Data Network)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: iso8583-client
  namespace: iso8583-data
spec:
  replicas: 3
  selector:
    matchLabels:
      app: iso8583-client
  template:
    metadata:
      labels:
        app: iso8583-client
    spec:
      nodeSelector:
        network: data
      containers:
      - name: client
        image: iso8583-client:latest
        ports:
        - containerPort: 8583
          name: iso8583
      - name: client-controller
        image: iso8583-controller:latest
        env:
        - name: NETWORK_MODE
          value: "sidecar"
        - name: CONTROL_ENDPOINT
          value: "controller.bank-control-eks.amazonaws.com:8080"
```

## Security Configuration

### Network Policies
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: control-network-isolation
spec:
  podSelector:
    matchLabels:
      network: control
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          network: control
  egress:
  - to:
    - podSelector:
        matchLabels:
          network: control
```

## Benefits

**Network Isolation**: Control and data traffic completely separated
**Cross-Region Resilience**: Bank and scheme in different AWS regions
**Automatic Failover**: Controllers communicate via isolated control network
**Scalable Architecture**: Independent scaling of data and control planes
**Security**: Network policies enforce traffic isolation
**Cost Optimization**: Right-sized instances for different workloads