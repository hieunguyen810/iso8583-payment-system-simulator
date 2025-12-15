#!/bin/bash

echo "Deploying ActiveMQ to Kubernetes..."

# Apply manifests
kubectl apply -f k8s/manifest/activemq-pvc.yaml
kubectl apply -f k8s/manifest/activemq-deployment.yaml
kubectl apply -f k8s/manifest/activemq-service.yaml
kubectl apply -f k8s/manifest/activemq-ingress.yaml

echo "Waiting for ActiveMQ to be ready..."
kubectl wait --for=condition=available --timeout=300s deployment/activemq

echo "ActiveMQ deployed successfully!"
echo "JMS URL: activemq-service:61616"
echo "Web Console: http://activemq.local (add to /etc/hosts)"
echo "Port Forward: kubectl port-forward svc/activemq-service 8161:8161"