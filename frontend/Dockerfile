# Stage 1 — build (node:22-alpine del R2, solo builder; npm ci determinista)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2 — nginx estáticos (nginx:stable-alpine, D8; busybox wget de serie)
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# Documental: el único puerto publicado de la pila sale vía compose (8080:80)
EXPOSE 80
# Sin HEALTHCHECK en el Dockerfile: los healthchecks viven en compose.yaml (DD-5)