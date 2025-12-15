#!/bin/bash

echo "Deploying ISO8583 ActiveMQ Stack to Kubernetes..."

# Deploy ActiveMQ first
# kubectl apply -f k8s/manifest/activemq-pvc.yaml
# kubectl apply -f k8s/manifest/activemq-statefulset.yaml
# kubectl apply -f k8s/manifest/activemq-service.yaml

echo "Waiting for ActiveMQ to be ready..."
kubectl wait --for=condition=ready pod -l app=activemq --timeout=300s

# Deploy applications
kubectl apply -f k8s/manifest/client-deployment-activemq.yaml
kubectl apply -f k8s/manifest/client-service.yaml
kubectl apply -f k8s/manifest/authorize-deployment-activemq.yaml
kubectl apply -f k8s/manifest/authorize-service.yaml

echo "Waiting for applications to be ready..."
kubectl wait --for=condition=available deployment/client-activemq --timeout=300s
kubectl wait --for=condition=available deployment/authorize-activemq --timeout=300s

echo "ActiveMQ Stack deployed successfully!"
echo "Client: http://client-service:8081"
echo "Authorize: http://authorize-service:8082"
echo "ActiveMQ Console: kubectl port-forward svc/activemq-service 8161:8161"