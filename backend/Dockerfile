FROM node:22-alpine

WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev
COPY db ./db
COPY src ./src
COPY resources ./resources

ENV NODE_ENV=production
EXPOSE 8080
CMD ["npm", "start"]
