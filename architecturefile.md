# ADAGS Hospital Management System — Architecture Document

> Generated: March 2026  
> Branch: `claude-pro`  
> App: Spring Boot 4.0.2 · Java 21 · PostgreSQL 17

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [High-Level Architecture](#3-high-level-architecture)
4. [Directory Structure](#4-directory-structure)
5. [Domain Model](#5-domain-model)
6. [Database Schema & Migrations](#6-database-schema--migrations)
7. [Security Architecture](#7-security-architecture)
8. [API Surface](#8-api-surface)
9. [Frontend Architecture](#9-frontend-architecture)
10. [Service Layer](#10-service-layer)
11. [Key Design Patterns](#11-key-design-patterns)
12. [Configuration & Profiles](#12-configuration--profiles)
13. [Known Dual-Domain Design](#13-known-dual-domain-design)
14. [Running the Application](#14-running-the-application)

---

## 1. Project Overview

ADAGS Hospital Management System is a full-stack web application built for managing the end-to-end operations of a hospital. It serves multiple staff roles through dedicated portals, each with its own workflow:

| Portal | Role(s) | Primary Responsibilities |
|--------|---------|--------------------------|
| Admin | `ADMIN` | User management, staff, pricing catalogue, revenue analytics, expense management |
| Receptionist | `RECEPTIONIST` | Patient registration, appointments, billing, invoices |
| Triage Nurse | `TRIAGE_NURSE`, `NURSE` | Triage assessments, vital signs recording |
| Doctor | `DOCTOR`, `SPECIALIST_DOCTOR` | Consultations, prescriptions, lab requests, surgery orders |
| Lab Technician | `LAB_TECHNICIAN` | Lab test processing, result entry, lab reports |
| Pharmacist | `PHARMACIST` | Prescription dispensing, stock/inventory management |
| Ward Nurse | `WARD_NURSE`, `NURSE` | Inpatient care, medication administration, wound care notes |

---

## 2. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.2 |
| Web | Spring MVC + Thymeleaf | 3.1.x |
| Security | Spring Security | 7.x |
| ORM | Hibernate / Spring Data JPA | 7.2.1.Final |
| Database | PostgreSQL | 17.6 |
| Migrations | Flyway | (via Spring Boot starter) |
| JWT | jjwt | 0.12.6 |
| Connection Pool | HikariCP | (via Spring Boot) |
| Build | Maven Wrapper | 3.14.x |
| Code Generation | Lombok, MapStruct | latest |
| Excel Import | Apache POI (OOXML) | — |
| API Documentation | SpringDoc OpenAPI (Swagger) | — |
| Frontend CSS | Bootstrap 5 (CDN) + Bootstrap Icons | — |
| Actuator | Spring Boot Actuator (3 endpoints exposed) | — |

---

## 3. High-Level Architecture

```
Browser
  │
  ├── GET/POST /login, /admin/**, /doctor/**, /receptionist/**, etc.
  │       │
  │       ▼
  │   [webFilterChain @Order(1)]
  │   Session-based · Form login · JSESSIONID cookie
  │       │
  │       ▼
  │   Thymeleaf Controllers → Thymeleaf Templates (HTML)
  │       │
  │       └── fetch() calls from JS in templates
  │               │
  │               ▼
  │           Ajax REST endpoints (same session cookie)
  │           /doctor/api/**, /receptionist/api/**, etc.
  │
  └── POST /auth/login → JWT access + refresh token
          │
          ▼
      [jwtFilterChain @Order(2)]
      Stateless · Bearer token · JwtAuthenticationFilter
          │
          ▼
      REST API controllers


PostgreSQL 17
  └── adags_hospital database
        └── Flyway migrations (V1–V26)


File System
  └── uploads/surgery-consent/{patientId}/  (consent PDF uploads)
```

The application has **two authentication channels**:
- **Web portal** — classic Spring MVC form login with HTTP sessions (JSESSIONID). All HTML pages, doctor/nurse/pharmacist portals.
- **REST API** — stateless JWT Bearer token authentication. Used by external clients or mobile apps via `/auth/login`.

Both channels share the same `AuthenticationProvider` (BCrypt + `CustomUserDetailsService`).

---

## 4. Directory Structure

```
hospital/
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/adags/hospital/
    │   │   ├── HospitalApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── DataSeeder.java              ← seeds default admin on startup
    │   │   │   ├── JwtProperties.java           ← reads jwt.* from properties
    │   │   │   ├── OpenApiConfig.java           ← Swagger/OpenAPI setup
    │   │   │   └── SecurityConfig.java          ← dual filter chain definition
    │   │   │
    │   │   ├── controller/
    │   │   │   ├── DoctorApiController.java     ← /doctor/api/patients, /visits
    │   │   │   ├── DoctorViewController.java    ← /doctor/* (page rendering)
    │   │   │   ├── LabRequestApiController.java ← /doctor/api/labtest/*
    │   │   │   ├── LabTechViewController.java   ← /labtech/*
    │   │   │   ├── LandingController.java
    │   │   │   ├── LoginController.java
    │   │   │   ├── PharmacistViewController.java← /pharmacist/*
    │   │   │   ├── PrescriptionApiController.java ← /doctor/api/rx/*
    │   │   │   ├── admin/                       ← /admin/**
    │   │   │   ├── appointment/
    │   │   │   ├── auth/AuthController.java     ← /auth/login, /auth/refresh
    │   │   │   ├── billing/BillingController.java
    │   │   │   ├── lab/LabController.java
    │   │   │   ├── medicalrecord/
    │   │   │   ├── nurse/                       ← /nurse/**
    │   │   │   ├── patient/
    │   │   │   ├── pharmacy/
    │   │   │   ├── pricing/PriceCatalogueApiController.java
    │   │   │   ├── receptionist/                ← /receptionist/**
    │   │   │   ├── staff/
    │   │   │   ├── triage/
    │   │   │   ├── user/
    │   │   │   └── wardnurse/                   ← /ward-nurse/**
    │   │   │
    │   │   ├── domain/
    │   │   │   ├── appointment/
    │   │   │   ├── auth/           RefreshToken
    │   │   │   ├── billing/        Invoice, InvoiceLineItem, Payment
    │   │   │   ├── common/         BaseEntity (UUID PK + audit timestamps)
    │   │   │   ├── expense/        Expense, Budget, SalaryRecord, ElectricityBill, WaterBill
    │   │   │   ├── lab/            LabRequest, LabResult, LabResultParameter
    │   │   │   ├── medicalrecord/  MedicalRecord, Diagnosis, Prescription (legacy)
    │   │   │   ├── patient/        Patient, NextOfKin
    │   │   │   ├── pharmacy/       Medication, InventoryItem, DispensedItem, StockBatch
    │   │   │   ├── pricing/        ServicePriceItem
    │   │   │   ├── staff/          Staff, Department
    │   │   │   ├── surgery/        SurgeryOrder, SurgeryIntraoperative, SurgeryPostopCare
    │   │   │   ├── triage/         TriageAssessment
    │   │   │   ├── user/           AppUser, Role (enum), Permission (enum)
    │   │   │   ├── visit/          VisitPrescription, VisitLabRequest  ← NEW (V26)
    │   │   │   └── ward/           WardPatientAssignment, VitalSigns, WoundCareNote,
    │   │   │                       MedicationAdministrationRecord
    │   │   │
    │   │   ├── dto/                (request/response DTOs per domain)
    │   │   ├── exception/          GlobalExceptionHandler, BusinessRuleException, etc.
    │   │   ├── repository/         (Spring Data JPA interfaces per domain)
    │   │   ├── security/
    │   │   │   ├── CustomUserDetailsService.java
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtService.java
    │   │   │   └── RoleBasedSuccessHandler.java
    │   │   └── service/
    │   │       ├── appointment/  billing/  consultation/(legacy)  expense/
    │   │       ├── lab/  medicalrecord/  patient/  pharmacy/  pricing/
    │   │       ├── staff/  surgery/  triage/  user/  ward/
    │   │       ├── auth/AuthService.java
    │   │       └── visit/PrescriptionLabService.java  ← NEW (V26)
    │   │
    │   └── resources/
    │       ├── application.properties           ← base (activates dev profile)
    │       ├── application-dev.properties       ← local Postgres, SQL logging
    │       ├── application-prod.properties      ← prod overrides
    │       ├── schema.sql                       ← ad-hoc patch file
    │       ├── db/migration/                    ← Flyway V1–V26
    │       ├── static/css/admin.css
    │       └── templates/
    │           ├── landing.html, login.html
    │           ├── admin/      (dashboard, users, patients, pricing, revenue,
    │           │                expenses, expense-analytics, nurse-form, fragments)
    │           ├── doctor/     (dashboard, consultation, lab-requests, lab-results,
    │           │                prescriptions, results, surgeries, surgery-detail,
    │           │                my-patients, ward-patients, ward-patient-detail, fragments)
    │           ├── labtech/    (dashboard, result-entry, history, lab-report, fragments)
    │           ├── nurse/      (dashboard, assessment, assessment-detail,
    │           │                completed-assessments, history, fragments)
    │           ├── pharmacist/ (dashboard, dispense, stock, history, fragments)
    │           ├── receptionist/ (dashboard, patients, patients-active, appointments,
    │           │                  billing, patient-profile, fragments)
    │           └── ward-nurse/ (dashboard, patient-care, history, fragments)
    └── test/
        └── java/com/adags/hospital/
```

---

## 5. Domain Model

### Base Entity

All domain entities extend `BaseEntity`:

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;           // auto-generated UUID primary key

    @CreationTimestamp
    private LocalDateTime createdAt;   // auto-set on insert

    @UpdateTimestamp
    private LocalDateTime updatedAt;   // auto-set on update

    private String createdBy;          // username of creator
}
```

### Core Entity Relationships

```
Patient (1) ──────┬──── (many) Appointment
                  ├──── (many) TriageAssessment
                  ├──── (many) MedicalRecord ───┬── (many) Diagnosis
                  │                              ├── (many) Prescription       [legacy]
                  │                              ├── (many) LabRequest          [legacy]
                  │                              ├── (many) VisitPrescription   [new V26]
                  │                              └── (many) VisitLabRequest     [new V26]
                  ├──── (many) Invoice ──────────── (many) InvoiceLineItem
                  │                  └──────────── (many) Payment
                  ├──── (many) WardPatientAssignment ─── VitalSigns
                  │                                   ─── WoundCareNote
                  │                                   ─── MedicationAdministrationRecord
                  └──── (many) SurgeryOrder ───── SurgeryIntraoperative
                                             ───── SurgeryPostopCare
                                             ───── SurgeryItemList
                                             ───── SurgeryAssignedNurse

Staff (1) ─────── (many) MedicalRecord (as attendingDoctor)
                ── (many) TriageAssessment (as nurse)
                ── belongs to Department

AppUser (1) ──── (1) Staff
              ── role: Role enum
              ── (many) RefreshToken
```

### Key Enumerations

| Enum | Values |
|------|--------|
| `ConsultationStatus` | `OPEN`, `LOCKED` |
| `InvoiceStatus` | `DRAFT`, `ISSUED`, `PAID`, `PARTIALLY_PAID`, `VOIDED` |
| `LineItemCategory` | `PHARMACY`, `LAB`, `CONSULTATION`, `SURGERY`, `WARD`, `OTHER` |
| `AppointmentStatus` | `SCHEDULED`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW` |
| `LabRequestStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `LabUrgency` | `ROUTINE`, `URGENT`, `STAT` |
| `TriagePriority` | `IMMEDIATE`, `URGENT`, `LESS_URGENT`, `NON_URGENT` |
| `SurgeryStatus` | `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `WardPatientStatus` | `ADMITTED`, `TRANSFERRED`, `DISCHARGED` |
| `PaymentMethod` | `CASH`, `INSURANCE`, `MOBILE_MONEY`, `BANK_TRANSFER` |

---

## 6. Database Schema & Migrations

Schema is managed entirely by **Flyway**. Migrations run automatically on startup.

| Migration | Description |
|-----------|-------------|
| V1 | Departments and Staff tables |
| V2 | App users (AppUser + Role mapping) |
| V3 | Patients |
| V4 | Appointments |
| V5 | Triage assessments |
| V6 | Medical records |
| V7 | Lab requests and results |
| V8 | Pharmacy inventory (medications, items) |
| V9 | Invoices, invoice_line_items, payments |
| V10 | Refresh tokens |
| V11 | Indexes (performance) |
| V12 | Patient occupation field |
| V13 | Patient address fields |
| V14 | Service price catalogue |
| V15 | Extended triage assessment fields |
| V16 | Extended consultation fields |
| V17 | Lab technician enhancements |
| V18 | Pharmacist enhancements |
| V19 | Expense management module |
| V20 | Ward nurse module (ward assignments, vital signs, wounds) |
| V21 | Patient marital status and insurance fields |
| V22 | Surgery module |
| V23 | Ward pricing |
| V24 | Surgery invoice ID link |
| V25 | Prescription price item link |
| **V26** | **Visit prescriptions and lab requests (new domain)** |

### Important FK Constraints

- `invoices.patient_id` → `patients.id` ON DELETE **RESTRICT**
- `invoices.medical_record_id` → `medical_records.id` ON DELETE SET NULL
- `payments.invoice_id` → `invoices.id` ON DELETE **RESTRICT**
- `triage_assessments.consultation_invoice_id` → `invoices.id` (nullable)
- `visit_prescriptions.medical_record_id` → `medical_records.id` ON DELETE CASCADE
- `visit_lab_requests.medical_record_id` → `medical_records.id` ON DELETE CASCADE
- `ward_patient_assignments.patient_id` → `patients.id`
- `surgery_orders.patient_id` → `patients.id` ON DELETE **RESTRICT**

---

## 7. Security Architecture

### Dual Filter Chain

```
Request
   │
   ├─ Matches /doctor/**, /admin/**, /receptionist/**, /nurse/**,
   │          /ward-nurse/**, /labtech/**, /pharmacist/**,
   │          /login, /logout, /css/**, /js/**
   │       │
   │       ▼   [webFilterChain — @Order(1)]
   │   Session-based auth (JSESSIONID)
   │   UsernamePasswordAuthenticationFilter
   │   RoleBasedSuccessHandler → redirects after login
   │   BCryptPasswordEncoder (strength=12)
   │   CSRF disabled (session context)
   │
   └─ Everything else
           │
           ▼   [jwtFilterChain — @Order(2)]
       Stateless (no session)
       JwtAuthenticationFilter → reads Bearer token from Authorization header
       Validates token via JwtService
       Security headers: X-Frame-Options DENY, XSS protection
```

### JWT Implementation

- Library: `jjwt 0.12.6`
- Signing: HMAC-SHA (key from `jwt.secret` property, Base64 encoded)
- Access token: short-lived (expiry from `jwt.access-token-expiration-ms`)
- Refresh token: longer-lived, stored in `refresh_tokens` table
- Claims payload: `sub` (username), `authorities` (list of role/permission strings), `iat`, `exp`
- Endpoints: `POST /auth/login` → returns `{accessToken, refreshToken}` · `POST /auth/refresh`

### Roles & Permissions Matrix

| Role | Key Permissions |
|------|----------------|
| `ADMIN` | ALL permissions |
| `RECEPTIONIST` | PATIENT R/W, APPOINTMENT R/W, BILLING_READ, STAFF_READ, TRIAGE_READ |
| `TRIAGE_NURSE` | PATIENT_READ, TRIAGE R/W, APPOINTMENT_READ, MEDICAL_RECORD_READ |
| `DOCTOR` | PATIENT_READ, MEDICAL_RECORD R/W, LAB R/W, APPOINTMENT R/W, PHARMACY_READ |
| `SPECIALIST_DOCTOR` | Same as DOCTOR |
| `LAB_TECHNICIAN` | PATIENT_READ, LAB R/W, MEDICAL_RECORD_READ |
| `PHARMACIST` | PATIENT_READ, PHARMACY R/W, DISPENSE_MEDICATION, MEDICAL_RECORD_READ |
| `ACCOUNTANT` | PATIENT_READ, BILLING R/W, PAYMENT_WRITE, PHARMACY_READ |
| `WARD_NURSE` | PATIENT_READ, MEDICAL_RECORD_READ, TRIAGE_READ, LAB_RESULT_READ |
| `NURSE` | PATIENT_READ, TRIAGE R/W, APPOINTMENT_READ, MEDICAL_RECORD_READ |

### Post-Login Routing (`RoleBasedSuccessHandler`)

Each role is redirected to its own portal after successful form login:

```
ADMIN              → /admin/dashboard
RECEPTIONIST       → /receptionist/dashboard
TRIAGE_NURSE/NURSE → /nurse/dashboard
DOCTOR/SPECIALIST  → /doctor/dashboard
LAB_TECHNICIAN     → /labtech/dashboard
PHARMACIST         → /pharmacist/dashboard
WARD_NURSE         → /ward-nurse/dashboard
```

### Password Encoding

BCrypt with strength 12 (`BCryptPasswordEncoder(12)`).

---

## 8. API Surface

### Web Portal Controllers (Thymeleaf — return view names)

| Controller | Base Path | Description |
|-----------|-----------|-------------|
| `LandingController` | `/` | Landing page |
| `LoginController` | `/login` | Login page |
| `AdminViewController` + sub-controllers | `/admin/**` | Admin portal pages |
| `DoctorViewController` | `/doctor/**` | Doctor portal pages |
| `LabTechViewController` | `/labtech/**` | Lab tech portal pages |
| `PharmacistViewController` | `/pharmacist/**` | Pharmacist portal pages |
| `NurseViewController` | `/nurse/**` | Nurse portal pages |
| `WardNurseViewController` | `/ward-nurse/**` | Ward nurse portal pages |
| `ReceptionistViewController` | `/receptionist/**` | Receptionist portal pages |

### REST API Controllers (return JSON)

| Controller | Base Path | Key Endpoints |
|-----------|-----------|---------------|
| `AuthController` | `/auth` | `POST /auth/login`, `POST /auth/refresh` |
| `DoctorApiController` | `/doctor/api` | `GET /patients/search`, `GET /patients/{id}/records`, `POST /patients/{id}/visits` |
| `PrescriptionApiController` | `/doctor/api/rx` | `GET /visit/{id}`, `POST /visit/{id}` (add), `PUT /{id}` (edit), `DELETE /{id}`, `GET /visit/{id}/lock-status`, `POST /visit/{id}/send-to-payment` |
| `LabRequestApiController` | `/doctor/api/labtest` | `GET /visit/{id}`, `POST /visit/{id}` (add), `PUT /{id}`, `DELETE /{id}`, `GET /visit/{id}/lock-status`, `POST /visit/{id}/send-to-payment` |
| `PriceCatalogueApiController` | `/doctor/api/catalogue` | `GET /pharmacy/search?q=`, `GET /lab/search?q=` |
| `PatientController` | `/receptionist/api/patients` | CRUD for patients |
| `AppointmentController` | `/receptionist/api/appointments` | Appointment management |
| `BillingController` | `/receptionist/api/billing` | Invoice and payment operations |
| `LabController` | `/labtech/api` | Lab test queue and result submission |
| `PharmacyController` | `/pharmacist/api` | Dispensing, stock management |
| `StaffController` | `/admin/api/staff` | Staff CRUD |
| `UserController` | `/admin/api/users` | User CRUD |
| `MedicalRecordController` | `/doctor/api/medicalrecord` | Consultation form submission |
| `TriageController` | `/nurse/api/triage` | Triage assessment CRUD |

### Swagger / OpenAPI

Available at: `http://localhost:8080/swagger-ui.html`  
Access restricted to `ADMIN` role.

---

## 9. Frontend Architecture

### Rendering Approach

Server-side rendering via **Thymeleaf 3.1**. No SPA framework (no React, Vue, or Angular). JavaScript is used selectively for:
- Dynamic search with debounce (patient lookup, drug catalogue)
- Accordion-style visit card loading (lazy fetch on expand)
- In-page CRUD without full page reload (add/edit/delete prescriptions and lab tests)
- Toast notifications

### Template Structure

Each role portal has a `fragments.html` containing reusable Thymeleaf fragments:

| Fragment | Content |
|----------|---------|
| `head` | `<head>` with Bootstrap 5 CDN, Bootstrap Icons CDN, meta tags |
| `sidebar` | Left navigation menu for the role |
| `topbar` | Top navigation bar with logged-in user info |
| `alerts` | Flash message/alert display area |
| `scripts` | Bootstrap JS bundle CDN + any shared JS |

Pages include these fragments via `th:replace`:
```html
<head th:replace="~{doctor/fragments :: head}"></head>
<div th:replace="~{doctor/fragments :: sidebar}"></div>
<th:block th:replace="~{doctor/fragments :: scripts}"></th:block>
```

### JavaScript Patterns Used (Doctor Portal)

The doctor's `lab-requests.html` and `prescriptions.html` use vanilla JS `fetch()` for a rich single-page-like experience:

```
Patient search (debounced)
    → GET /doctor/api/patients/search?q=
    → render patient dropdown

Patient selected
    → GET /doctor/api/patients/{id}/records
    → render visit accordion cards

Visit card expanded
    → GET /doctor/api/rx/visit/{id}   (or /labtest/...)
    → lazy-render prescription/lab table + add form

Add item
    → POST /doctor/api/rx/visit/{id}
    → re-render item table in that card only

Send to Reception
    → POST /doctor/api/rx/visit/{id}/send-to-payment
    → update status badge in-place (no full reload)
```

### CSS

- Bootstrap 5 loaded from CDN in every `fragments.html`
- Custom stylesheet: `static/css/admin.css` (admin portal only)
- No PostCSS, Sass, or build pipeline — plain CSS

---

## 10. Service Layer

### Service Classes and Responsibilities

| Service | Location | Responsibility |
|---------|----------|---------------|
| `AuthService` | `service/auth` | JWT login, refresh token creation/validation |
| `PatientService` | `service/patient` | Patient CRUD, search |
| `AppointmentService` | `service/appointment` | Appointment CRUD |
| `TriageService` | `service/triage` | Triage assessment CRUD |
| `MedicalRecordService` | `service/medicalrecord` | Medical record creation and retrieval |
| `ConsultationService` | `service/consultation` | **LEGACY** — handles old Prescription/LabRequest domain |
| `PrescriptionLabService` | `service/visit` | **NEW (V26)** — VisitPrescription and VisitLabRequest with auto-invoicing |
| `BillingService` | `service/billing` | Invoice and payment CRUD |
| `AdminRevenueService` | `service/billing` | Revenue analytics and dashboard metrics |
| `LabService` | `service/lab` | Lab request queue for lab tech portal |
| `LabTechService` | `service/lab` | Result entry, lab reports |
| `PharmacyService` | `service/pharmacy` | Inventory management |
| `PharmacistService` | `service/pharmacy` | Dispensing workflow |
| `StockService` | `service/pharmacy` | Stock batch management |
| `SurgeryService` | `service/surgery` | Surgery order lifecycle |
| `WardNurseService` | `service/ward` | Ward admission, vitals, medication administration |
| `ExpenseService` | `service/expense` | Expense tracking, budgets, salary records |
| `PriceCatalogueService` | `service/pricing` | Service price item CRUD, Excel import |
| `StaffService` | `service/staff` | Staff CRUD |
| `UserService` | `service/user` | AppUser management, password changes |
| `DataSeeder` | `config` | Seeds initial admin user and default data on startup |

### PrescriptionLabService — Auto-Invoicing Logic

Every time a prescription or lab test is added via `addLabRequest()` or `addPrescription()`, the service **automatically creates or updates an invoice**:

```
addLabRequest(recordId, priceItemId, ...) or addPrescription(recordId, ...)
    │
    └── addInvoiceLineItem(record, description, category, price)
            │
            ├── Look for existing DRAFT invoice for visit + category
            │       (reuse to group items on same invoice)
            │
            ├── If none found → create new Invoice (status=DRAFT)
            │
            ├── Append InvoiceLineItem to invoice
            │
            ├── Recalculate subtotal + totalAmount
            │
            └── Set invoice.status = ISSUED (visible to receptionist)
```

`confirmSentToPayment()` is a safety net — since invoices are already ISSUED, it:
1. Validates at least one item exists
2. Promotes any stray DRAFT invoices to ISSUED
3. Returns invoice numbers and total to display in the UI

---

## 11. Key Design Patterns

### 1. Repository Pattern
All data access through Spring Data JPA interfaces. Custom JPQL queries annotated with `@Query`. No raw SQL in Java code (except schema.sql patch file).

### 2. DTO Pattern
Separate request/response DTOs for all API endpoints. Domain entities are never serialized directly to JSON (except in some internal doctor API endpoints that use `Map<String, Object>`).

### 3. Service Layer Isolation
Controllers are thin — they delegate all business logic to service classes. Services are `@Transactional` where needed.

### 4. Role-Based Access Control (RBAC)
Two layers:
- URL-level: `SecurityConfig` route guards per role
- Method-level: `@EnableMethodSecurity` enables `@PreAuthorize`, `@Secured` annotations

### 5. Flyway Database Versioning
Every schema change is a numbered migration script `V{n}__{description}.sql`. Never modify existing migrations — always add new ones.

### 6. Thymeleaf Fragment Reuse
Each portal has a single `fragments.html`. All pages include fragments rather than duplicating navigation HTML. Ensures consistent UI across all pages.

### 7. Lazy Loading in Doctor Portal
Visit body content (prescriptions/lab tests) is loaded on-demand when a visit card is expanded (`toggleVisit()`). The `_visitLoaded` map tracks which visits have already been fetched to prevent duplicate requests.

---

## 12. Configuration & Profiles

### Active Profile

Set in `application.properties`:
```properties
spring.profiles.active=dev
```

### Dev Profile (`application-dev.properties`)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/adags_hospital
spring.datasource.username=${DB_USERNAME:adags_user}
spring.datasource.password=${DB_PASSWORD:adags_pass}
spring.jpa.show-sql=true
spring.thymeleaf.cache=false       ← templates reload instantly without restart
server.error.include-message=always
server.error.include-stacktrace=always
logging.level.com.adags.hospital=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### Prod Profile (`application-prod.properties`)

- Thymeleaf cache enabled
- SQL logging disabled
- Error details hidden from responses
- DB credentials via environment variables

### HikariCP Pool (Dev)

- Pool name: `HospitalHikariPool-Dev`
- Max pool size: 10
- Min idle: 2
- Connection timeout: 20s
- Idle timeout: 5 min

### Actuator

3 endpoints exposed. Health check available at `/actuator/health` (publicly accessible — no auth required).

---

## 13. Known Dual-Domain Design

The codebase contains **two parallel implementations** for prescriptions and lab requests. This is a deliberate incremental migration from the legacy domain to the new visit-centric domain introduced in V26.

| Aspect | Legacy Domain | New Domain (V26) |
|--------|--------------|------------------|
| Prescription entity | `Prescription` | `VisitPrescription` |
| Lab request entity | `LabRequest` | `VisitLabRequest` |
| Service | `ConsultationService` | `PrescriptionLabService` |
| API base path | `/doctor/api/prescriptions`, `/doctor/api/labrequests` | `/doctor/api/rx`, `/doctor/api/labtest` |
| Invoicing | Manual (separate action) | Automatic on every item add |
| Price linking | Post-hoc via `service_price_item_id` FK | Required at creation via `ServicePriceItem` |
| UI pages | `doctor/consultation.html` | `doctor/prescriptions.html`, `doctor/lab-requests.html` |
| Status when sent | Sets `consultationStatus = FINALIZED` | Sets invoice to `ISSUED` (status stays `OPEN`) |

The **old domain** still powers the classic consultation form page. The **new domain** powers the redesigned standalone prescription and lab-request tabs. The eventual goal is to retire `ConsultationService` once the new domain is proven in production.

---

## 14. Running the Application

### Prerequisites

- Java 21
- PostgreSQL 17 running locally on port 5432
- Database `adags_hospital` created
- User `adags_user` with password `adags_pass` (or set `DB_USERNAME` / `DB_PASSWORD` env vars)

### Start

```powershell
cd hospital
.\mvnw.cmd spring-boot:run
```

Application starts on **http://localhost:8080**

### Clean Build (if class files are stale)

```powershell
.\mvnw.cmd clean spring-boot:run
```

### Kill Existing Instance (if port 8080 in use)

```powershell
netstat -ano | findstr :8080
taskkill /PID <pid> /F
```

### Default Admin Login

Seeded by `DataSeeder` on first startup. Check `DataSeeder.java` for the default username/password.

### Database Reset (all patient data)

```powershell
$env:PGPASSWORD="adags_pass"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U adags_user -d adags_hospital -f delete_all_patients.sql
```

---

*Document generated from codebase inspection — March 2026*
