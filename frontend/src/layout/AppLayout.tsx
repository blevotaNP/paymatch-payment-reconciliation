import { AppShell, Avatar, Burger, Group, Menu, NavLink, Text, UnstyledButton } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { IconApi, IconBuildingStore, IconChevronDown, IconFileInvoice, IconLogout } from '@tabler/icons-react'
import { NavLink as RouterNavLink, Outlet, useNavigate } from 'react-router'
import { useCurrentUser, useLogout } from '../api/auth'
import { Logo } from './Logo'

const NAV_ITEMS = [
  { to: '/invoices', label: 'Invoices', icon: IconFileInvoice },
  { to: '/counterparties', label: 'Counterparties', icon: IconBuildingStore },
]

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure()
  const { data: user } = useCurrentUser()
  const logout = useLogout()
  const navigate = useNavigate()

  const initials = user?.displayName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 232, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="lg"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Logo />
          </Group>

          <Menu position="bottom-end" width={200}>
            <Menu.Target>
              <UnstyledButton>
                <Group gap={8}>
                  <Avatar size={30} radius="xl" color="brand">
                    {initials}
                  </Avatar>
                  <Text size="sm" fw={500} visibleFrom="xs">
                    {user?.displayName}
                  </Text>
                  <IconChevronDown size={14} />
                </Group>
              </UnstyledButton>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Label>Signed in as {user?.username}</Menu.Label>
              <Menu.Item leftSection={<IconApi size={16} />} component="a" href="/swagger-ui.html" target="_blank">
                API documentation
              </Menu.Item>
              <Menu.Divider />
              <Menu.Item
                color="red"
                leftSection={<IconLogout size={16} />}
                onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login') })}
              >
                Log out
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            component={RouterNavLink}
            to={item.to}
            label={item.label}
            leftSection={<item.icon size={18} stroke={1.6} />}
            onClick={close}
            styles={{ root: { borderRadius: 'var(--mantine-radius-md)' }, label: { fontWeight: 500 } }}
          />
        ))}
      </AppShell.Navbar>

      <AppShell.Main>
        <div style={{ maxWidth: 1360, margin: '0 auto' }}>
          <Outlet />
        </div>
      </AppShell.Main>
    </AppShell>
  )
}
