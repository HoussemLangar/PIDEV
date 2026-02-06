FROM php:8.1-apache

# Installation dépendances système + ssl-cert
RUN apt-get update && apt-get install -y \
    git \
    unzip \
    libicu-dev \
    libzip-dev \
    libpng-dev \
    libjpeg-dev \
    libfreetype6-dev \
    ssl-cert \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install -j$(nproc) \
    pdo \
    pdo_mysql \
    intl \
    zip \
    gd \
    opcache

# Générer certificat SSL local
RUN make-ssl-cert generate-default-snakeoil --force-overwrite

# Activer modules Apache
RUN a2enmod rewrite \
 && a2enmod ssl \
 && a2ensite default-ssl

# Installation Composer
COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

# Config Apache Symfony
RUN sed -i 's!/var/www/html!/var/www/html/public!g' /etc/apache2/sites-available/000-default.conf

# ServerName pour éviter warning
RUN echo "ServerName localhost" >> /etc/apache2/apache2.conf

# Permissions
RUN chown -R www-data:www-data /var/www

WORKDIR /var/www/html

EXPOSE 80
EXPOSE 443
