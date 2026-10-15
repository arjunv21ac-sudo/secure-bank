# 🏦 SecureBank: Mini Banking App

A full stack banking application. Customers open accounts (approved by bank staff), deposit, withdraw and **transfer money safely**, see their history and download **PDF statements**. The focus is on getting money right under failure and concurrency: **all-or-nothing transfers, no deadlocks, no double payments, no overdrafts**.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security, JWT, Spring Data JPA (Hibernate) |
| Database | MySQL 8 (H2 in-memory for tests) |
| PDF | OpenPDF (monthly statements) |
| API docs | Swagger / OpenAPI |
| Testing | JUnit 5, MockMvc, multi-threaded concurrency tests |
| Frontend | React 19, React Router 7, Axios, Vite |

## Features

**Customer**
- Register / login (JWT, 30-minute sessions)
- Open Savings or Current accounts (start as **PENDING** until approved)
- Deposit and withdraw
- **Transfer** to any account number, with the receiver's masked name shown first ("Kavya S.")
- Saved beneficiaries
- Transaction history with date filters and pagination
- **Monthly PDF statement** with opening balance, running balance and closing balance

**Admin (bank staff)**
- Approve / reject new accounts (account number generated on approval)
- Freeze / unfreeze accounts
- Unlock customers locked out after wrong passwords
- Dashboard: customers, accounts by status, total deposits, today's transfers

## How money is kept correct

| Problem | Solution | Where |
|---|---|---|
| Transfer fails halfway (debit done, credit not) | One `@Transactional` method: debit + credit + audit row commit together or roll back together | `BankingService.transfer` |
| Two requests change one balance at once | `SELECT ... FOR UPDATE` row lock (`@Lock(PESSIMISTIC_WRITE)`) before reading the balance | `AccountRepository.findByIdForUpdate` |
| A→B and B→A at the same moment (**deadlock**) | Always lock the two accounts in the same order: lower id first | `BankingService.transfer` |
| Double-click / network retry pays twice | **Idempotency-Key** header: the same key returns the first result; a unique constraint is the final guard | `TransferController`, `BankingService.findPrevious` |
| Stale data after waiting for a lock | Money methods run at `READ_COMMITTED` (MySQL defaults to `REPEATABLE_READ`) | `BankingService` |
| Rounding errors (`0.1 + 0.2 = 0.30000000000000004`) | `BigDecimal` everywhere, exactly 2 decimal places | entities, DTOs |
| Overdraft and daily limit races | Balance and ₹50,000 daily limit checked **while the row is locked** | `checkSufficientBalance`, `checkDailyLimit` |
| Password guessing | Login locks after **3 wrong passwords** (HTTP 423); only an admin can unlock | `LoginAttemptService` |
| Audit | Transactions are insert-only, with the balance after each one | `BankTransaction` |

## Screens

| Page | Who | What it does |
|---|---|---|
| Accounts (`/dashboard`) | Customer | Total balance, account cards with status, request a new account |
| Account details | Customer | Deposit / withdraw, PDF statement by month, history with date filters and pagination |
| Transfer | Customer | 3 steps: details → **review with the receiver's verified name** → receipt; double-click safe |
| Beneficiaries | Customer | Save verified payees, "Pay" shortcut |
| Admin panel | Staff | Live stats, approve / reject, freeze / unfreeze, unlock customers |

**Double-submit protection is layered:** the Transfer page ignores repeat clicks (a `useRef` flag that updates instantly), and every attempt of one transfer reuses the same `Idempotency-Key`, so even requests that slip through are paid once by the backend.

## Database

```
users ──1:N──▶ accounts        (a customer can hold Savings + Current)
accounts ──1:N──▶ transactions (from_account / to_account; deposit = no "from", withdrawal = no "to")
users ──1:N──▶ beneficiaries   (saved payees)
```

## API endpoints

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Public |
| GET / POST | `/api/accounts` | Customer (list / open) |
| GET | `/api/accounts/{id}` | Owner |
| GET | `/api/accounts/lookup/{accountNumber}` | Logged in |
| POST | `/api/accounts/{id}/deposit`, `/withdraw` | Owner |
| GET | `/api/accounts/{id}/transactions?from=&to=&page=` | Owner |
| GET | `/api/accounts/{id}/statement?month=2026-10` | Owner (PDF) |
| POST | `/api/transfers` + header `Idempotency-Key` | Owner of the source account |
| GET / POST / DELETE | `/api/beneficiaries` | Customer |
| GET | `/api/admin/dashboard`, `/api/admin/accounts?status=`, `/api/admin/customers` | Admin |
| POST | `/api/admin/accounts/{id}/approve` · `reject` · `freeze` · `unfreeze` | Admin |
| POST | `/api/admin/customers/{id}/unlock` | Admin |

## Run the backend

**Requirements:** JDK 17+ and MySQL 8 on port 3306.

1. Set your MySQL password in `backend/src/main/resources/application.properties` (or the `DB_PASSWORD` environment variable)
2. Run `SecureBankApplication` in Eclipse, or:
   ```bash
   cd backend
   mvnw spring-boot:run
   ```
3. Swagger UI: http://localhost:8080/swagger-ui.html

## Run the frontend

**Requirements:** Node.js 20+, with the backend running.

```bash
cd frontend
npm install
npm run dev
```
Open http://localhost:5173. Vite forwards `/api` calls to the backend on port 8080.

### Demo accounts (created on first run)

| Role | Email | Password |
|---|---|---|
| Admin | admin@securebank.com | Admin@1234 |
| Customer | rahul@securebank.com | Rahul@1234 |
| Customer | kavya@securebank.com | Kavya@1234 |
| Customer (account pending approval) | meena@securebank.com | Meena@1234 |

### Tests (no MySQL needed)
```bash
cd backend
mvnw test
```

| Test | What it proves |
|---|---|
| `BankingFlowTest.fullBankingFlow` | Approval, deposit, transfer, idempotent retry, daily limit, insufficient balance, ownership (403), history, PDF statement, freeze |
| `BankingFlowTest.loginLocksAfterThreeWrongPasswords` | 3 wrong passwords lock the login (423); admin unlock restores it |
| `ConcurrencyTest.oppositeTransfersNeverDeadlockAndKeepTotalMoney` | 40 simultaneous A→B / B→A transfers: no deadlock, total money unchanged |
| `ConcurrencyTest.concurrentWithdrawalsNeverOverdraw` | 20 simultaneous withdrawals of 100 from 1,000: exactly 10 succeed, balance never negative |
| `ConcurrencyTest.duplicateSubmissionsMoveMoneyOnce` | The same transfer sent 10 times at once: money moves once |
