import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcrypt';
import 'dotenv/config';

const prisma = new PrismaClient();

async function main() {
  // Clear existing data
  try {
    await prisma.transaction.deleteMany();
    await prisma.bankAccount.deleteMany();
    await prisma.user.deleteMany();
  } catch (e) {
    console.log('Collections might not exist yet, skipping deleteMany');
  }

  const hashedPassword = await bcrypt.hash('password123', 10);

  // Create a user
  const user = await prisma.user.create({
    data: {
      email: 'user@example.com',
      name: 'John Doe',
      password: hashedPassword,
    },
  });

  // Create bank accounts
  const accounts = [
    {
      bankName: 'SBI Bank',
      accountNumber: '6828',
      balance: 5000.0,
      color: '#0052B4',
      logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/cc/State_Bank_of_India_logo.svg/1024px-State_Bank_of_India_logo.svg.png',
      userId: user.id,
    },
    {
      bankName: 'UPI Lite',
      accountNumber: 'UPI-123',
      balance: 150.0,
      color: '#41A4F4',
      logoUrl: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR0Y64iS0m_9f_1x_0Y-5_0_K_W_k_9_9_9_9&s',
      userId: user.id,
    },
    {
      bankName: 'Paytm',
      accountNumber: 'PAYTM-99',
      balance: 0.0,
      color: '#FFFFFF',
      logoUrl: 'https://logo.clearbit.com/paytm.com',
      userId: user.id,
    },
  ];

  for (const account of accounts) {
    await prisma.bankAccount.create({ data: account });
  }

  console.log('Seed data created successfully!');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
