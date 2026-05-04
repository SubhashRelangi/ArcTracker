import type { Response } from 'express';
import type { AuthRequest } from '../middleware/auth.middleware.js';
import prisma from '../utils/prisma.js';

export const getAccounts = async (req: AuthRequest, res: Response) => {
  try {
    const accounts = await prisma.bankAccount.findMany({
      where: { userId: req.userId }
    });
    res.json(accounts);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch accounts' });
  }
};

export const createAccount = async (req: AuthRequest, res: Response) => {
  const { bankName, accountNumber, balance, color, logoUrl } = req.body;
  try {
    const account = await prisma.bankAccount.create({
      data: {
        userId: req.userId!,
        bankName,
        accountNumber,
        balance: balance || 0,
        color: color || '#FFFFFF',
        logoUrl
      }
    });
    res.status(201).json(account);
  } catch (error) {
    res.status(500).json({ error: 'Failed to create account' });
  }
};
