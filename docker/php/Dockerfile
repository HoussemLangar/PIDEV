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

# Configuration PHP pour les uploads de fichiers
RUN echo "upload_max_filesize = 50M" > /usr/local/etc/php/conf.d/uploads.ini \
    && echo "post_max_size = 50M" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "max_input_time = 300" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "max_execution_time = 300" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "default_socket_timeout = 300" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "memory_limit = 256M" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "session.gc_maxlifetime = 3600" >> /usr/local/etc/php/conf.d/uploads.ini

# Configure Apache timeout for large file uploads
RUN echo "TimeOut 300" >> /etc/apache2/apache2.conf

# Permissions
RUN chown -R www-data:www-data /var/www

WORKDIR /var/www/html

EXPOSE 80
EXPOSE 443
