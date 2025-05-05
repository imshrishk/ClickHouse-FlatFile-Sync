# Build stage
FROM node:20-alpine AS build

WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install dependencies
RUN npm install

# Set environment variable for build
ARG VITE_SPRING_BOOT_URL
ENV VITE_SPRING_BOOT_URL=${VITE_SPRING_BOOT_URL:-http://backend:8080/api}

# Print the environment variable for debugging
RUN echo "Building with VITE_SPRING_BOOT_URL=${VITE_SPRING_BOOT_URL}"

# Copy all frontend files (excluding node_modules due to .dockerignore)
COPY . .

# Create .env file
RUN echo "VITE_SPRING_BOOT_URL=${VITE_SPRING_BOOT_URL}" > .env

# Build the application
RUN npm run build

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
