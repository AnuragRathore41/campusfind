# 🔎 CampusFind – Lost & Found Portal

A full-stack web app where students report lost and found items on campus, search through them, and claim items that belong to them. Reporters (or admins) approve or reject each claim.

## Problem
Lost items on campus usually depend on notice boards and word of mouth, so valuables are often never recovered. CampusFind gives the campus one searchable place for lost and found items, with a verification step so items go to the right owner.

## Features
- Register and login with JWT authentication
- Role-based access (Student / Admin)
- Report lost or found items with photo upload
- Search and filter by keyword, category, location, type and status
- Claim workflow: claim → reporter reviews → approve / reject → item resolved
- Admin can delete items
- Input validation and consistent JSON error responses

## Tech Stack
Java 17, Spring Boot, Spring Security, JWT, Spring Data JPA (Hibernate), MySQL, HTML / CSS / JavaScript

## How it works
- Passwords are hashed with BCrypt
- The JWT carries the user's role, and a custom filter validates it on every request
- Claim rules: you cannot claim your own item, claim twice, or claim a resolved item
- Only the reporter or an admin can approve claims or edit an item
- Images are saved on the server with random file names, and only the URL is stored in the database

## API Overview
| Method | Endpoint | Access |
|--------|----------|--------|
| POST | /api/auth/register | Public |
| POST | /api/auth/login | Public |
| GET | /api/items | Logged in |
| GET | /api/items/search | Logged in |
| POST | /api/items | Logged in |
| PUT | /api/items/{id} | Reporter / Admin |
| DELETE | /api/items/{id} | Admin |
| POST | /api/items/{id}/image | Reporter / Admin |
| POST | /api/items/{id}/claim | Logged in |
| GET | /api/items/{id}/claims | Reporter / Admin |
| PUT | /api/claims/{id}/approve | Reporter / Admin |
| PUT | /api/claims/{id}/reject | Reporter / Admin |

## Run locally
1. Install Java 17+ and MySQL
2. Create the database: `CREATE DATABASE campusfind_db;`
3. Create a file named `secrets.properties` in the project root:
   ```
   DB_PASSWORD=your_mysql_password
   JWT_SECRET=a_random_string_of_at_least_32_characters
   ```
4. Run `CampusfindApplication`
5. Open http://localhost:8080

To create an admin, register normally, then run:
`UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';`

## Screenshots
(add screenshots here)

## Live Demo
(coming soon)