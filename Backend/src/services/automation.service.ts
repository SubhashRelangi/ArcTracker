import cron from 'node-cron';
import prisma from '../utils/prisma.js';

const CATEGORIES = ['Housing', 'Food', 'Transport', 'Shopping', 'Leisure'];
const MERCHANTS = {
  Housing: ['Rent', 'Mortgage', 'Home Insurance', 'Maintenance'],
  Food: ['Starbucks', 'McDonalds', 'Subway', 'Local Cafe', 'Grocery Store'],
  Transport: ['Uber', 'Shell', 'Gas Station', 'Public Transit', 'Train Ticket'],
  Shopping: ['Amazon', 'Walmart', 'Target', 'Apple', 'H&M'],
  Leisure: ['Netflix', 'Spotify', 'Cinema', 'Gaming Store', 'Gym Membership']
};

export const startAutomation = () => {
  // Run immediately on start for testing/demo purposes
  console.log('Starting automation service...');
  generateDailyTransactions().catch(err => console.error('Initial generation failed:', err));

  // Run every day at midnight
  cron.schedule('0 0 * * *', async () => {
    console.log('Running automated daily transactions...');
    await generateDailyTransactions();
  });
};

export const generateDailyTransactions = async () => {
  try {
    const accounts = await prisma.bankAccount.findMany();
    
    for (const account of accounts) {
      // Generate 3 transactions per account
      for (let i = 0; i < 3; i++) {
        const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)]!;
        const merchants = MERCHANTS[category as keyof typeof MERCHANTS];
        const merchant = merchants[Math.floor(Math.random() * merchants.length)]!;
        const amount = parseFloat((Math.random() * 50 + 5).toFixed(2));

        await prisma.$transaction(async (tx) => {
          await tx.transaction.create({
            data: {
              accountId: account.id,
              amount: -amount,
              category,
              merchant,
              type: 'EXPENSE',
              subtext: `Automated payment to ${merchant}`,
              date: new Date(),
            }
          });

          await tx.bankAccount.update({
            where: { id: account.id },
            data: {
              balance: {
                decrement: amount
              }
            }
          });
        });
      }
    }
  } catch (error) {
    console.error('Automation error:', error);
  }
};
