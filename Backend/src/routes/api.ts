import { Router } from 'express';
import * as accountController from '../controllers/account.controller.js';
import * as transactionController from '../controllers/transaction.controller.js';
import * as authController from '../controllers/auth.controller.js';
import * as userController from '../controllers/user.controller.js';
import { authenticate } from '../middleware/auth.middleware.js';
import { validate } from '../middleware/validate.middleware.js';
import { 
  loginSchema, 
  signupSchema, 
  createAccountSchema, 
  createTransactionSchema 
} from '../utils/validation-schemas.js';

const router = Router();

/**
 * @swagger
 * /auth/signup:
 *   post:
 *     summary: Register a new user
 *     tags: [Auth]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [email, password, name]
 *             properties:
 *               email: { type: string }
 *               password: { type: string }
 *               name: { type: string }
 *     responses:
 *       201:
 *         description: User created successfully
 */
router.post('/auth/signup', validate(signupSchema), authController.signup);

/**
 * @swagger
 * /auth/login:
 *   post:
 *     summary: Login a user
 *     tags: [Auth]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [email, password]
 *             properties:
 *               email: { type: string }
 *               password: { type: string }
 *     responses:
 *       200:
 *         description: Login successful
 */
router.post('/auth/login', validate(loginSchema), authController.login);

/**
 * @swagger
 * /users/me:
 *   get:
 *     summary: Get current user profile
 *     tags: [User]
 *     security: [{ bearerAuth: [] }]
 *     responses:
 *       200:
 *         description: User profile data
 */
router.get('/users/me', authenticate, userController.getMe);

/**
 * @swagger
 * /accounts:
 *   get:
 *     summary: Get all bank accounts for user
 *     tags: [Accounts]
 *     security: [{ bearerAuth: [] }]
 *     responses:
 *       200:
 *         description: List of bank accounts
 *   post:
 *     summary: Create a new bank account
 *     tags: [Accounts]
 *     security: [{ bearerAuth: [] }]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [bankName, accountNumber]
 *             properties:
 *               bankName: { type: string }
 *               accountNumber: { type: string }
 *               balance: { type: number }
 *               color: { type: string }
 *     responses:
 *       201:
 *         description: Account created successfully
 */
router.get('/accounts', authenticate, accountController.getAccounts);
router.post('/accounts', authenticate, validate(createAccountSchema), accountController.createAccount);

/**
 * @swagger
 * /transactions:
 *   get:
 *     summary: Get user transactions
 *     tags: [Transactions]
 *     security: [{ bearerAuth: [] }]
 *     parameters:
 *       - in: query
 *         name: search
 *         schema: { type: string }
 *         description: Search by merchant or category
 *     responses:
 *       200:
 *         description: List of transactions
 *   post:
 *     summary: Create a new transaction
 *     tags: [Transactions]
 *     security: [{ bearerAuth: [] }]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [accountId, amount, category, merchant, type]
 *             properties:
 *               accountId: { type: string }
 *               amount: { type: number }
 *               category: { type: string }
 *               merchant: { type: string }
 *               type: { type: string, enum: [EXPENSE, INCOME] }
 *     responses:
 *       201:
 *         description: Transaction created successfully
 */
router.get('/transactions', authenticate, transactionController.getTransactions);
router.post('/transactions', authenticate, validate(createTransactionSchema), transactionController.createTransaction);

/**
 * @swagger
 * /spending:
 *   get:
 *     summary: Get spending summary by category
 *     tags: [Transactions]
 *     security: [{ bearerAuth: [] }]
 *     responses:
 *       200:
 *         description: Spending breakdown
 */
router.get('/spending', authenticate, transactionController.getSpendingSummary);

export default router;
