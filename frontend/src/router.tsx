import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layout/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'

// pages are loaded on demand; the charts library alone is bigger than the rest of the app
const page = <T extends string>(load: () => Promise<Record<T, React.ComponentType>>, name: T) =>
  lazy(() => load().then((module) => ({ default: module[name] })))

const DashboardPage = page(() => import('./pages/dashboard/DashboardPage'), 'DashboardPage')
const ReviewPage = page(() => import('./pages/review/ReviewPage'), 'ReviewPage')
const StatementsPage = page(() => import('./pages/statements/StatementsPage'), 'StatementsPage')
const TransactionsPage = page(() => import('./pages/transactions/TransactionsPage'), 'TransactionsPage')
const InvoicesPage = page(() => import('./pages/invoices/InvoicesPage'), 'InvoicesPage')
const CounterpartiesPage = page(() => import('./pages/counterparties/CounterpartiesPage'), 'CounterpartiesPage')
const ReportsPage = page(() => import('./pages/reports/ReportsPage'), 'ReportsPage')
const ActivityPage = page(() => import('./pages/activity/ActivityPage'), 'ActivityPage')

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'review', element: <ReviewPage /> },
      { path: 'statements', element: <StatementsPage /> },
      { path: 'transactions', element: <TransactionsPage /> },
      { path: 'invoices', element: <InvoicesPage /> },
      { path: 'counterparties', element: <CounterpartiesPage /> },
      { path: 'reports', element: <ReportsPage /> },
      { path: 'activity', element: <ActivityPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
