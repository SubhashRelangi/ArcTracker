import { z } from 'zod';

export const loginSchema = z.object({
  body: z.object({
    email: z.string().email('Invalid email address'),
    password: z.string().min(6, 'Password must be at least 6 characters')
  })
});

export const signupSchema = z.object({
  body: z.object({
    email: z.string().email('Invalid email address'),
    password: z.string().min(6, 'Password must be at least 6 characters'),
    name: z.string().min(2, 'Name is required')
  })
});

export const createAccountSchema = z.object({
  body: z.object({
    bankName: z.string().min(1, 'Bank name is required'),
    accountNumber: z.string().min(4, 'Account number must be at least 4 digits'),
    balance: z.number().optional(),
    color: z.string().regex(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/, 'Invalid hex color').optional(),
    logoUrl: z.string().url('Invalid URL').optional()
  })
});

export const createTransactionSchema = z.object({
  body: z.object({
    accountId: z.string().min(1, 'Account ID is required'),
    amount: z.number().refine(n => n !== 0, 'Amount cannot be zero'),
    category: z.string().min(1, 'Category is required'),
    merchant: z.string().min(1, 'Merchant is required'),
    subtext: z.string().optional(),
    logoUrl: z.string().url('Invalid URL').optional(),
    type: z.enum(['EXPENSE', 'INCOME'])
  })
});
