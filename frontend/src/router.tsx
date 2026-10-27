import { createBrowserRouter, Navigate } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layout/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { CounterpartiesPage } from './pages/counterparties/CounterpartiesPage'
import { ActivityPage } from './pages/activity/ActivityPage'
import { InvoicesPage } from './pages/invoices/InvoicesPage'
import { ReviewPage } from './pages/review/ReviewPage'
import { StatementsPage } from './pages/statements/StatementsPage'
import { TransactionsPage } from './pages/transactions/TransactionsPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/review" replace /> },
      { path: 'review', element: <ReviewPage /> },
      { path: 'activity', element: <ActivityPage /> },
      { path: 'invoices', element: <InvoicesPage /> },
      { path: 'counterparties', element: <CounterpartiesPage /> },
      { path: 'statements', element: <StatementsPage /> },
      { path: 'transactions', element: <TransactionsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
