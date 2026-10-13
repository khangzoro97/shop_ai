FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite inlines these at build time.
ARG VITE_ZALO_PHONE=
ARG VITE_CONTACT_EMAIL=
ARG VITE_FREE_TRIAL_IMAGES=5
ENV VITE_ZALO_PHONE=$VITE_ZALO_PHONE \
    VITE_CONTACT_EMAIL=$VITE_CONTACT_EMAIL \
    VITE_FREE_TRIAL_IMAGES=$VITE_FREE_TRIAL_IMAGES
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
