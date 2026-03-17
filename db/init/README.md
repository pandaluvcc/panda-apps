# Database Configuration

This directory contains database initialization scripts for Panda Apps.

## Databases

| Database | Description | Default User |
|----------|-------------|--------------|
| gridtrading_db | Grid Trading application database | gridtrading_user |
| snapledger_db | Snap Ledger application database | snapledger_user |

## Character Set

All databases use:
- Character Set: `utf8mb4`
- Collation: `utf8mb4_unicode_ci`

## Usage

### MySQL/MariaDB

Execute the initialization script:

```bash
mysql -u root -p < db/init/01-create-databases.sql
```

### Docker Compose

If using Docker, you can mount this script to `/docker-entrypoint-initdb.d/`:

```yaml
services:
  mysql:
    image: mysql:8.0
    volumes:
      - ./db/init:/docker-entrypoint-initdb.d
```

## Security Notes

1. **Change default passwords**: Replace `your_password` with strong, unique passwords
2. **Restrict remote access**: Consider removing `'%'` host grants if remote access is not needed
3. **Use environment variables**: Store credentials in environment variables or a secrets manager

## Environment Variables

Configure your applications with the following:

```bash
# Grid Trading DB
GRIDTRADING_DB_URL=jdbc:mysql://localhost:3306/gridtrading_db
GRIDTRADING_DB_USER=gridtrading_user
GRIDTRADING_DB_PASSWORD=your_password

# Snap Ledger DB
SNAPLEDGER_DB_URL=jdbc:mysql://localhost:3306/snapledger_db
SNAPLEDGER_DB_USER=snapledger_user
SNAPLEDGER_DB_PASSWORD=your_password
```

## Script Order

Scripts are executed in alphabetical order:
1. `01-create-databases.sql` - Creates databases and grants privileges
