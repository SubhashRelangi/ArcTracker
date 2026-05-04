import type { Response } from 'express';
import type { AuthRequest } from '../middleware/auth.middleware.js';
import prisma from '../utils/prisma.js';

export const getTransactions = async (req: AuthRequest, res: Response) => {
  const { search } = req.query;
  try {
    const transactions = await prisma.transaction.findMany({
      where: {
        account: {
          userId: req.userId
        },
        OR: search ? [
          { merchant: { contains: String(search), mode: 'insensitive' } },
          { category: { contains: String(search), mode: 'insensitive' } },
        ] : undefined
      },
      orderBy: { date: 'desc' },
      take: 50
    });
    res.json(transactions);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch transactions' });
  }
};

export const createTransaction = async (req: AuthRequest, res: Response) => {
  const { accountId, amount, category, merchant, subtext, logoUrl, type } = req.body;
  try {
    // Verify account belongs to user
    const account = await prisma.bankAccount.findFirst({
      where: { id: accountId, userId: req.userId }
    });

    if (!account) {
      return res.status(403).json({ error: 'Unauthorized to use this account' });
    }

    const result = await prisma.$transaction(async (tx) => {
      const transaction = await tx.transaction.create({
        data: {
          accountId,
          amount,
          category,
          merchant,
          subtext,
          logoUrl,
          type
        }
      });

      // Update balance
      if (type === 'EXPENSE') {
        await tx.bankAccount.update({
          where: { id: accountId },
          data: { balance: { decrement: Math.abs(amount) } }
        });
      } else {
        await tx.bankAccount.update({
          where: { id: accountId },
          data: { balance: { increment: Math.abs(amount) } }
        });
      }

      return transaction;
    });

    res.status(201).json(result);
  } catch (error) {
    res.status(500).json({ error: 'Failed to create transaction' });
  }
};

export const getSpendingSummary = async (req: AuthRequest, res: Response) => {
  try {
    const spending = await prisma.transaction.groupBy({
      by: ['category'],
      where: { 
        type: 'EXPENSE',
        account: {
          userId: req.userId
        }
      },
      _sum: { amount: true }
    });
    res.json(spending);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch spending summary' });
  }
};
