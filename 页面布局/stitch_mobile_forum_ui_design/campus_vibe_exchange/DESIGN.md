---
name: Campus Vibe & Exchange
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#3c4a42'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#6c7a71'
  outline-variant: '#bbcabf'
  surface-tint: '#006c49'
  primary: '#006c49'
  on-primary: '#ffffff'
  primary-container: '#10b981'
  on-primary-container: '#00422b'
  inverse-primary: '#4edea3'
  secondary: '#9d4300'
  on-secondary: '#ffffff'
  secondary-container: '#fd761a'
  on-secondary-container: '#5c2400'
  tertiary: '#b4136d'
  on-tertiary: '#ffffff'
  tertiary-container: '#ff72b1'
  on-tertiary-container: '#740043'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#6ffbbe'
  primary-fixed-dim: '#4edea3'
  on-primary-fixed: '#002113'
  on-primary-fixed-variant: '#005236'
  secondary-fixed: '#ffdbca'
  secondary-fixed-dim: '#ffb690'
  on-secondary-fixed: '#341100'
  on-secondary-fixed-variant: '#783200'
  tertiary-fixed: '#ffd9e4'
  tertiary-fixed-dim: '#ffb0cd'
  on-tertiary-fixed: '#3e0022'
  on-tertiary-fixed-variant: '#8c0053'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 26px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
  price-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '700'
    lineHeight: 24px
  price-symbol:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 0.75rem
  margin: 0.75rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system targets Gen-Z college students and campus communities engaging in secondhand trading, peer-to-peer inquiries, social sharing, and campus life updates. The design aesthetic is modern, refreshing, and lively, merging tactile modern card interfaces with fluid mobile micro-interactions.

The brand persona is youthful, dependable, friendly, and community-centric. It removes visual clutter to highlight vibrant user-generated media, clear pricing tags, and structured classification chips. High-contrast typography paired with soft-tinted backgrounds fosters an intuitive, relaxed browsing experience tailored for both lightweight mini-programs (WeChat Mini Programs) and standalone native iOS/Android applications.

## Colors

The color palette centers on clean emerald greens, high-energy warm accents, and a tiered neutral structure designed for sunlight readability and long browsing sessions.

- **Primary (`#10B981`)**: The campus signature green. Represents vitality, youth, freshness, and sustainable circular trading. Used for main CTA buttons, the active floating action tab, active navigation states, verified badges, and active filter tabs.
- **Secondary (`#F97316`)**: Warm coral orange. Exclusively highlights price markers (e.g. `¥55`), urgent campus trade labels, discount tags, and prompt badges.
- **Tertiary (`#EC4899`)**: Soft bubblegum pink. Drives social touchpoints like "Confession Wall / 表白墙", emotional reactions, like counters, and peer appreciation.
- **Neutral (`#F8FAFC`)**: Soft cool-tinted paper white for page canvas backgrounds, paired with pure `#FFFFFF` for elevated content cards, `#0F172A` for primary headlines, `#475569` for body copy, and `#94A3B8` for metadata and timestamps.

## Typography

The typography scale utilizes **Plus Jakarta Sans** for Latin characters and numbers, gracefully falling back to standard Chinese system sans-serif typefaces (PingFang SC, Hiragino Sans GB, Microsoft YaHei).

- **Price Hierarchy**: Prices always lead with `price-symbol` (`¥`, 13px bold) tightly spaced next to `price-lg` (20px bold) in the orange secondary token.
- **Hashtags and Categories**: Topic tags (`#其他`, `#二手数码`) use `label-md` with semibold weight directly rendered in the primary green hue to anchor subject themes.
- **Secondary Details**: Timestamps, view counts, and dorm pick-up locations adopt `body-sm` (`#94A3B8`) to preserve scannability without cluttering the view.

## Layout & Spacing

The layout is built for mobile-first feed experiences using a compact 4-column structure on standard smartphones (360px–430px) and auto-collapsing dual columns on larger tablet viewports.

- **Outer Margins**: Fixed at `0.75rem` (12px) on mobile to maximize card surface area while avoiding edge collision.
- **Card Spacing**: Information feed cards are separated by a vertical rhythm of `space-md` (12px).
- **Internal Card Padding**: Content cards apply `1rem` (16px) internal padding, while secondary sub-elements (like user headers and interaction bars) stay spaced apart with `0.5rem` to `0.75rem`.
- **Bottom Navigation Clearance**: All scrollable views mandate an end padding of `5.5rem` (88px) to guarantee clean clearance above the persistent bottom navigation bar and the elevated center publish capsule.

## Elevation & Depth

Visual hierarchy leverages crisp surface separation, translucent overlays, and tinted ambient shadows rather than harsh borders:

- **Level 0 (Canvas Base)**: `#F8FAFC` flat surface.
- **Level 1 (Feed Cards & Top App Bar)**: Pure white `#FFFFFF` surface backed by a soft, diffuse green-tinted shadow: `box-shadow: 0 4px 16px -2px rgba(16, 185, 129, 0.05), 0 2px 6px -1px rgba(15, 23, 42, 0.04)`.
- **Level 2 (Pills, Chips & Search Inputs)**: Neutral `#F1F5F9` background with no shadow, shifting to `rgba(16, 185, 129, 0.12)` upon activation.
- **Level 3 (Floating Publish Button)**: Elevated with a multi-layered brand glow: `box-shadow: 0 8px 24px -4px rgba(16, 185, 129, 0.42), 0 4px 10px -2px rgba(16, 185, 129, 0.25)`.
- **Level 4 (Modal Sheets & Bottom Sheets)**: Crisp white cards elevated with `0 20px 30px -10px rgba(15, 23, 42, 0.15)`.

## Shapes

The interface embraces organic, approachable curvatures that reflect the youthfulness of student life:

- **Cards & Media Panels**: `rounded-lg` (16px / 1rem). Every image attachment, trade preview card, and post wrapper uses 16px corner radii to balance smoothness and optimal image crop retention.
- **Pills, Badges & Search Bars**: Pill-shaped (`rounded-full`, 9999px / minimum 20px). Secondary filter tags, school switchers, category bubbles, and search fields use full stadium rounding.
- **Floating Action Button**: Complete circle (56px × 56px, `rounded-full`).

## Components

### 1. Navigation & Search Bar
- **School Selector**: Pill shape (`#F1F5F9`), containing a soft grey user/pin icon, school name in `body-sm` font, and a subtle down chevron.
- **Search Capsule**: Extended pill input with light `#F1F5F9` background, `#94A3B8` icon, and placeholder "搜一搜" (Search).
- **Category Tabs**: Horizontal scrolling row of text tabs. The active tab displays bold primary text with a 3px thick, 20px wide primary green pill indicator anchored 4px underneath.

### 2. Category & Filter Chips
- **Status Chips (e.g., "闲置", "求购")**: Small pill badge placed top-right of posts with light mint background (`rgba(16, 185, 129, 0.1)`) and solid primary green text (`#10B981`).
- **Emoji Quick-Filter Chips**: Stadium pill buttons displaying a colored emoji alongside label text (`今日热门`, `数码`, `图书`). Default state has `#F1F5F9` fill; selected state receives `#10B981` tint and border.

### 3. Community Feed & Secondhand Post Card
- **Header**: 40px circular user avatar, nickname (`label-md`), transaction method badge ("校内自提" in `#F1F5F9` pill with `#64748B` text), and relative timestamp aligned right.
- **Content Area**: Topic tag in primary color `#10B981`, post body text in `#0F172A`, followed by item price in `#F97316` (secondary).
- **Media Presentation**: 1 to 9 image grid with 12px internal gaps and 16px rounded outer corners.
- **Footer Interaction Bar**: View count with eye icon, paired on the right with thumb-up count (`#F59E0B` or tertiary pink on tap) and comment counter.

### 4. Floating Action Button (Publish Capsule)
- Placed dead-center within the bottom navigation bar.
- Dimensions: 54px × 54px circle protruding 16px above the tab bar baseline.
- Solid primary emerald background (`#10B981`) with pure white 24px plus icon and brand glow shadow.

### 5. Tab Bar
- Flat `#FFFFFF` bar with subtle top separator border (`#F1F5F9`).
- 4 navigation items with center clearance for the floating publish button: Home (首页), Secondhand (二手), Publish (+), Confession Wall (表白墙), Profile (我的).