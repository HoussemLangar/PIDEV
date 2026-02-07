FROM php:8.2-fpm

RUN apt-get update && apt-get install -y \
    curl \
    git \
    default-mysql-client \
    && docker-php-ext-install \
    pdo \
    pdo_mysql \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Composer
COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

WORKDIR /srv/app

COPY . .

RUN composer install --prefer-dist --no-interaction --optimize-autoloader

EXPOSE 9000

CMD ["php-fpm"]
