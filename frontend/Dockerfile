# Build stage
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files
COPY package*.json ./

# Configure npm and install dependencies with retry logic
RUN npm config set registry https://registry.npmjs.org/ && \
    npm config set fetch-retries 5 && \
    npm config set fetch-retry-mintimeout 20000 && \
    npm config set fetch-retry-maxtimeout 120000 && \
    npm install --no-audit --no-fund --prefer-offline --network-timeout=100000

# Set environment variable for build
ARG VITE_SPRING_BOOT_URL
ENV VITE_SPRING_BOOT_URL=${VITE_SPRING_BOOT_URL:-http://backend:8080/api}

# Print the environment variable for debugging
RUN echo "Building with VITE_SPRING_BOOT_URL=${VITE_SPRING_BOOT_URL}"

# Copy all frontend files (excluding node_modules due to .dockerignore)
COPY . .

# Create .env file
RUN echo "VITE_SPRING_BOOT_URL=${VITE_SPRING_BOOT_URL}" > .env

# Build the application using npx to ensure vite is available
RUN npx vite build

# Production stage
FROM nginx:alpine

# Copy build output from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy test.html for fallback testing
COPY client/public/test.html /usr/share/nginx/html/test.html

# For debugging - list files in html directory
RUN ls -la /usr/share/nginx/html

# Expose port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
