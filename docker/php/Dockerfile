FROM php:8.2-apache

# Installation dépendances système + ssl-cert
RUN apt-get update && apt-get install -y \
    git \
    unzip \
    curl \
    ca-certificates \
    gnupg \
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

# Installation Blackfire PHP Probe (optionnel - commenté pour éviter issues DNS)
# RUN install -m 0755 -d /etc/apt/keyrings \
#     && curl -fsSL https://packages.blackfire.io/gpg.key | gpg --dearmor -o /etc/apt/keyrings/blackfire.gpg \
#     && echo "deb [signed-by=/etc/apt/keyrings/blackfire.gpg] https://packages.blackfire.io/debian any main" > /etc/apt/sources.list.d/blackfire.list \
#     && apt-get update \
#     && apt-get install -y blackfire-php \
#     && rm -rf /var/lib/apt/lists/*

# Générer certificat SSL local
RUN make-ssl-cert generate-default-snakeoil --force-overwrite

# Activer modules Apache
RUN a2enmod rewrite \
 && a2enmod ssl \
 && a2enmod status

# Expose /server-status uniquement pour localhost et réseau Docker interne.
RUN printf '%s\n' \
    'ExtendedStatus On' \
    '<Location /server-status>' \
    '    SetHandler server-status' \
    '    Require all granted' \
    '</Location>' \
    > /etc/apache2/conf-available/server-status.conf \
    && a2enconf server-status

# Installation Composer
COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

# Copier configuration VirtualHost
COPY symfony.conf /etc/apache2/sites-available/symfony.conf

# Désactiver le site par défaut et activer notre VirtualHost
RUN a2dissite 000-default.conf default-ssl.conf \
 && a2ensite symfony.conf

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

# Startup entrypoint to fix bind-mount permissions for Symfony var/.
COPY docker-entrypoint.sh /usr/local/bin/pidev-entrypoint.sh
RUN chmod +x /usr/local/bin/pidev-entrypoint.sh

# Permissions
RUN chown -R www-data:www-data /var/www

WORKDIR /var/www/html

EXPOSE 80
EXPOSE 443

ENTRYPOINT ["/usr/local/bin/pidev-entrypoint.sh"]
CMD ["apache2-foreground"]
