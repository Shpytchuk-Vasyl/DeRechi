import {
  ArrowDown,
  ArrowUp,
  Bell,
  CalendarDays,
  ChartColumn,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  CircleOff,
  CircleX,
  ClipboardList,
  Clock,
  Cloud,
  CloudRain,
  CreditCard,
  Database,
  Droplet,
  Ellipsis,
  Flame,
  FlaskConical,
  Gauge,
  Heart,
  History,
  House,
  Image,
  Info,
  LayoutGrid,
  Lock,
  Mail,
  MapPin,
  Megaphone,
  MessageCircle,
  Minus,
  Music,
  Pause,
  Pencil,
  Play,
  Plus,
  Search,
  Send,
  Settings,
  Shield,
  ShoppingBag,
  Signal,
  SkipBack,
  SkipForward,
  Smile,
  Sparkles,
  Star,
  Sun,
  Sword,
  Tag,
  Target,
  Trash2,
  TrendingUp,
  TriangleAlert,
  Trophy,
  User,
  Users,
  Wand,
  Wind,
  X,
  Zap,
  type LucideProps,
} from 'lucide-react'
import type { ComponentType, ReactElement } from 'react'

// One glyph, one role. Swapping the icon library happens here and nowhere else.
const ICONS = {
  overview: Gauge,
  chart: ChartColumn,
  log: ClipboardList,
  channels: Megaphone,
  activity: Signal,
  lab: FlaskConical,
  history: History,
  performance: TrendingUp,
  settings: Settings,
  database: Database,
  menu: LayoutGrid,
  up: ArrowUp,
  down: ArrowDown,
  flat: Minus,
  ok: Check,
  warn: TriangleAlert,
  fail: CircleX,
  info: Info,
  idle: CircleDot,
  off: CircleOff,
  live: Zap,
  draft: Pencil,
  target: Target,
  alerts: Bell,
  add: Plus,
  remove: Trash2,
  search: Search,
  close: X,
  expand: ChevronDown,
  prev: ChevronLeft,
  next: ChevronRight,
  photo: Image,
  heart: Heart,
  'heart-filled': Heart,
  comment: MessageCircle,
  play: Play,
  pause: Pause,
  rewind: SkipBack,
  forward: SkipForward,
  sun: Sun,
  cloud: Cloud,
  rain: CloudRain,
  star: Star,
  calendar: CalendarDays,
  clock: Clock,
  user: User,
  users: Users,
  mail: Mail,
  lock: Lock,
  send: Send,
  home: House,
  trophy: Trophy,
  flame: Flame,
  sparkle: Sparkles,
  music: Music,
  cart: ShoppingBag,
  tag: Tag,
  dots: Ellipsis,
  sword: Sword,
  shield: Shield,
  wand: Wand,
  smile: Smile,
  card: CreditCard,
  pin: MapPin,
  wind: Wind,
  drop: Droplet,
} as const

export type IconName = keyof typeof ICONS

const SIZES = { sm: 16, md: 20, lg: 32 } as const

interface Props {
  name: IconName
  size?: keyof typeof SIZES
  label?: string
}

export function Icon({ name, size = 'md', label }: Props) {
  const Glyph = ICONS[name] as ComponentType<LucideProps>
  return (
    <Glyph
      size={SIZES[size]}
      color="currentColor"
      strokeWidth={2.4}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  )
}

export type IconLike = IconName | ReactElement

export function renderIcon(icon: IconLike, size: keyof typeof SIZES = 'md'): ReactElement {
  return typeof icon === 'string' ? <Icon name={icon} size={size} /> : icon
}
