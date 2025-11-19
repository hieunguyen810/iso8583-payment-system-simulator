#!/bin/bash

echo "Installing Gatekeeper..."
kubectl apply -f gatekeeper.yaml

echo "Waiting for Gatekeeper to be ready..."
kubectl wait --for=condition=available --timeout=300s deployment/gatekeeper-controller-manager -n gatekeeper-system

echo "Installing constraint template..."
kubectl apply -f constraint-template.yaml

echo "Installing constraint..."
kubectl apply -f constraint.yaml

echo "Gatekeeper setup complete!"
echo "Test with: kubectl apply -f ../test-deployment.yaml"