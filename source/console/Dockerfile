# Build stage
FROM node:18-alpine AS build

WORKDIR /app

COPY package*.json ./

RUN npm ci --only=production=false && \
    npm cache clean --force

# Copy source files
COPY . .

# Build the app
RUN npm run build && \
    # Remove source maps in production (optional, saves ~30-40%)
    find ./build -name '*.map' -type f -delete

# Runtime stage
FROM nginx:1.25-alpine

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init gettext

# Create non-root user for nginx
RUN addgroup -S nginx-runtime && \
    adduser -S nginx-runtime -G nginx-runtime && \
    chown -R nginx-runtime:nginx-runtime /var/cache/nginx && \
    chown -R nginx-runtime:nginx-runtime /var/log/nginx && \
    chown -R nginx-runtime:nginx-runtime /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown -R nginx-runtime:nginx-runtime /var/run/nginx.pid

# Copy built assets
COPY --from=build --chown=nginx-runtime:nginx-runtime /app/build /usr/share/nginx/html

# Copy configuration files
COPY --chown=nginx-runtime:nginx-runtime nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=nginx-runtime:nginx-runtime config-template.js /tmp/config-template.js
COPY --chown=nginx-runtime:nginx-runtime entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

USER nginx-runtime

EXPOSE 8080

ENTRYPOINT ["dumb-init", "--"]
CMD ["/entrypoint.sh"]