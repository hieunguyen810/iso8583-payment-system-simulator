#!/bin/sh

# Set default values
API_BASE_URL=${API_BASE_URL:-"http://localhost:8081/api/iso8583"}
FARO_URL=${FARO_URL:-"https://faro-collector-prod-ap-southeast-1.grafana.net/collect/<app_key>"}
METRICS_URL=${METRICS_URL:-"http://localhost:12348/v1/metrics"}
FARO_ENABLED=${FARO_ENABLED:-"true"}
ENVIRONMENT=${ENVIRONMENT:-"development"}

# Generate config.js directly
cat > /usr/share/nginx/html/config.js << EOF
window.APP_CONFIG = {
  API_BASE_URL: '${API_BASE_URL}',
  FARO_URL: '${FARO_URL}',
  METRICS_URL: '${METRICS_URL}',
  FARO_ENABLED: ${FARO_ENABLED},
  ENVIRONMENT: '${ENVIRONMENT}'
};
EOF

# Start nginx
nginx -g "daemon off;"