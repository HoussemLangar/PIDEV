# Pharmacy CRUD System

## Overview
Complete CRUD system for managing pharmacies with dual interfaces (back office and front office), server-side validation, search, sort, and PDF export functionality.

## Architecture

### Back Office (Admin)
**Base URL:** `/admin/pharmacy`
**Controller:** `src/Controller/PharmacyBackController.php`

#### Routes:
- `GET /admin/pharmacy` - List all pharmacies (with search, sort, pagination)
- `GET /admin/pharmacy/new` - Display create form
- `POST /admin/pharmacy` - Create new pharmacy
- `GET /admin/pharmacy/{id}/edit` - Display edit form
- `POST /admin/pharmacy/{id}` - Update pharmacy
- `POST /admin/pharmacy/{id}/delete` - Delete pharmacy (POST with confirmation)
- `GET /admin/pharmacy/{id}/pdf` - Export pharmacy details as PDF

#### Features:
- Search by name, address, phone, or email
- Sort by: id, nom (name), adresse (address), createdAt
- Direction: asc/desc
- Server-side validation (no HTML5 validation attributes)
- Error display with detailed feedback
- Flash messages for success/failure
- PDF export of pharmacy details

#### Templates:
- `templates/back/pharmacy/index.html.twig` - Pharmacy list with search/sort controls
- `templates/back/pharmacy/form.html.twig` - Create/edit form with server-side error display
- `templates/back/pharmacy/pdf.html.twig` - PDF template for pharmacy export

### Front Office (Public)
**Base URL:** `/pharmacy`
**Controller:** `src/Controller/PharmacyFrontController.php`

#### Routes:
- `GET /pharmacy` - List all pharmacies (with search, sort)
- `GET /pharmacy/{id}` - Display pharmacy details

#### Features:
- Search by name, address, phone, or email
- Sort by: nom (name), adresse (address), createdAt
- Responsive card-based layout
- Detailed pharmacy information page

#### Templates:
- `templates/front/pharmacy/list.html.twig` - Pharmacy directory with search/sort
- `templates/front/pharmacy/show.html.twig` - Pharmacy detail page

## Validation Rules

All validation is **server-side only**. No HTML5 validation attributes or JavaScript.

### Pharmacy Entity:
- **nom (Name):**
  - Required
  - Max 150 characters

- **adresse (Address):**
  - Required
  - Max 255 characters

- **telephone (Phone):**
  - Optional
  - Max 20 characters

- **email (Email):**
  - Optional
  - Must be valid email format
  - Max 100 characters

- **horaires (Hours):**
  - Optional
  - Max 255 characters

## Database Layer

### Repository Methods

**PharmacyRepository.php:**
- `findAll()` - Get all pharmacies (ordered by ID DESC)
- `findById(int $id)` - Get single pharmacy
- `save(Pharmacy $entity, bool $flush = true)` - Persist pharmacy
- `delete(Pharmacy $entity, bool $flush = true)` - Remove pharmacy
- `searchPharmacies(?string $search, string $sortBy, string $sortDir)` - Search with filtering and sorting

#### searchPharmacies() Method:
```php
searchPharmacies(
    ?string $search = null,      // Search term (searches nom, adresse, telephone, email)
    string $sortBy = 'id',       // Sort field (id, nom, adresse, createdAt)
    string $sortDir = 'ASC'      // Sort direction (ASC, DESC)
): Pharmacy[]
```

Search is case-insensitive using DQL `LOWER()` function.

## File Structure

```
src/Controller/
├── PharmacyBackController.php      # Admin CRUD operations
└── PharmacyFrontController.php     # Public pharmacy listing

src/Repository/
└── PharmacyRepository.php          # Data access with search method

templates/back/pharmacy/
├── index.html.twig                 # Admin list with search/sort
├── form.html.twig                  # Admin create/edit form
└── pdf.html.twig                   # PDF export template

templates/front/pharmacy/
├── list.html.twig                  # Public pharmacy directory
└── show.html.twig                  # Public pharmacy details
```

## Usage Examples

### Back Office - Create Pharmacy
```
1. Navigate to /admin/pharmacy
2. Click "Créer une pharmacie"
3. Fill form fields
4. Submit
5. System validates on server
6. On error: Returns to form with error messages highlighted
7. On success: Redirects to list with success flash message
```

### Back Office - Search
```
URL: /admin/pharmacy?search=pharmacy_name&sort=nom&dir=asc
```

### Front Office - Public List
```
1. Navigate to /pharmacy
2. Use search form to filter
3. Select sort field (nom, adresse, createdAt)
4. Toggle sort direction
5. Click pharmacy card to view details
```

### PDF Export
```
1. From admin list, click "PDF" button for any pharmacy
2. Browser downloads pharmacy details as PDF
3. Or access via: /admin/pharmacy/{id}/pdf
```

## Server-Side Validation Flow

### Create/Update Process:
1. Form submitted with POST data
2. Controller receives request
3. Entity populated with form data
4. Validation rules checked:
   - Required fields validated
   - String length limits checked
   - Email format validated (if provided)
5. If validation fails:
   - Error messages collected in `$errors` array
   - Form re-displayed with error messages
   - No data persisted
6. If validation passes:
   - Entity persisted to database
   - Flash message shown
   - Redirect to list or detail page

### Error Display:
Errors shown with Bootstrap `.invalid-feedback` class:
```html
{% if errors.nom %}
    <div class="invalid-feedback">{{ errors.nom }}</div>
{% endif %}
```

## Business Features

### Search
- Case-insensitive search across: nom, adresse, telephone, email
- Works on both back office and front office
- Preserves search term in form for resubmission

### Sort
- Back office: id, nom, adresse, createdAt
- Front office: nom, adresse, createdAt
- Direction: asc/desc toggleable
- Default: id (back), nom (front)

### PDF Export
- Currently HTML-based PDF template
- Contains pharmacy name, address, contact info, hours
- Includes metadata (ID, creation date, modification date)
- Requires dompdf or similar library for actual PDF generation

### Relationships
- Pharmacy has optional relationship to Pharmacien (pharmacy owner)
- Pharmacien has relationship to User entity
- Front office displays pharmacien name if available

## Integration Notes

### Required Dependencies:
- Symfony 6.x
- Doctrine ORM
- Twig templating
- Bootstrap CSS (for styling)
- Font Awesome icons (for UI elements)

### Optional Dependencies:
- dompdf or similar for actual PDF generation (currently HTML template only)
- For API integration: FOSRestBundle or API Platform

## Testing Checklist

- [ ] Back office list loads and displays pharmacies
- [ ] Search filters results correctly
- [ ] Sort functionality works in both directions
- [ ] Create form validation displays errors
- [ ] Create successful entry persists to database
- [ ] Edit form loads existing data
- [ ] Update validation works
- [ ] Delete confirmation works
- [ ] PDF export generates HTML
- [ ] Front office list displays pharmacies
- [ ] Front office show page displays pharmacy details
- [ ] Search works on front office
- [ ] Sort works on front office
- [ ] Navigation between pages works

## Future Enhancements

- [ ] API endpoints for CRUD operations (JSON responses)
- [ ] Pagination (currently returns all results)
- [ ] Bulk operations (edit/delete multiple)
- [ ] CSV/Excel export
- [ ] Advanced filtering (by pharmacien, date range, etc.)
- [ ] PDF actual rendering (dompdf integration)
- [ ] Email notifications
- [ ] User roles and permissions
- [ ] Audit logging

## Notes

- All validation happens on server (no form validation on client)
- Flash messages use Bootstrap alert styling
- Templates extend from base templates (back/base.html.twig, front base.html.twig)
- Icons from Font Awesome 6
- Responsive design with Bootstrap grid system
