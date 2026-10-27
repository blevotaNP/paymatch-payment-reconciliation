import { Alert, Button, Center, Paper, PasswordInput, Stack, Text, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { IconAlertCircle } from '@tabler/icons-react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useCurrentUser, useLogin } from '../api/auth'
import { Logo } from '../layout/Logo'

export function LoginPage() {
  const { data: user } = useCurrentUser()
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const form = useForm({
    initialValues: { username: '', password: '' },
    validate: {
      username: (value) => (value.trim() ? null : 'Enter your username'),
      password: (value) => (value ? null : 'Enter your password'),
    },
  })

  if (user) return <Navigate to={from} replace />

  return (
    <Center mih="100vh" p="md">
      <Stack w="100%" maw={380} gap="lg">
        <Center>
          <Logo size="lg" />
        </Center>
        <Paper p="xl" shadow="sm">
          <form
            onSubmit={form.onSubmit((values) =>
              login.mutate(values, { onSuccess: () => navigate(from, { replace: true }) }),
            )}
          >
            <Stack>
              <div>
                <Text fw={600} size="lg">
                  Sign in
                </Text>
                <Text size="sm" c="dimmed">
                  Reconcile bank payments with your invoices.
                </Text>
              </div>
              {login.error && (
                <Alert color="red" icon={<IconAlertCircle size={18} />} py="xs">
                  {login.error.message}
                </Alert>
              )}
              <TextInput label="Username" autoComplete="username" autoFocus {...form.getInputProps('username')} />
              <PasswordInput label="Password" autoComplete="current-password" {...form.getInputProps('password')} />
              <Button type="submit" loading={login.isPending} fullWidth mt="xs">
                Sign in
              </Button>
            </Stack>
          </form>
        </Paper>
        <Text size="xs" c="dimmed" ta="center">
          Demo accounts: <b>anna</b> or <b>piotr</b>, password <b>demo1234</b>
        </Text>
      </Stack>
    </Center>
  )
}
