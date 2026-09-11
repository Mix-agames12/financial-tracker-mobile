import React from 'react';
import { FlexWidget, TextWidget } from 'react-native-android-widget';
import type { WidgetSummary } from './widgetData';

// Importa react-native-android-widget: cargar sólo con require() cuando isWidgetSupported es true.

export type WidgetTheme = 'light' | 'dark';

const PALETTE = {
  light: { bg: '#ffffff', text: '#0f172a', muted: '#475569', income: '#059669', expense: '#e11d48', accent: '#4f46e5' },
  dark: { bg: '#1e293b', text: '#f1f5f9', muted: '#94a3b8', income: '#34d399', expense: '#fb7185', accent: '#818cf8' },
} as const;

export function SummaryWidget({ data, theme }: { data: WidgetSummary; theme: WidgetTheme }) {
  const c = PALETTE[theme];

  return (
    <FlexWidget
      clickAction="OPEN_APP"
      style={{
        height: 'match_parent',
        width: 'match_parent',
        backgroundColor: c.bg,
        borderRadius: 20,
        padding: 14,
        flexDirection: 'column',
        justifyContent: 'space-between',
      }}
    >
      <FlexWidget style={{ width: 'match_parent', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <TextWidget text={data.appName} style={{ fontSize: 13, fontWeight: 'bold', color: c.accent }} maxLines={1} truncate="END" />
        <TextWidget text={data.monthLabel} style={{ fontSize: 11, color: c.muted }} />
      </FlexWidget>

      <FlexWidget style={{ flexDirection: 'column' }}>
        <TextWidget text="Balance total" style={{ fontSize: 11, color: c.muted }} />
        <TextWidget
          text={data.balance}
          style={{ fontSize: 22, fontWeight: 'bold', color: data.isNegative ? c.expense : c.text }}
          maxLines={1}
        />
      </FlexWidget>

      <FlexWidget style={{ width: 'match_parent', flexDirection: 'row', justifyContent: 'space-between' }}>
        <FlexWidget style={{ flexDirection: 'column' }}>
          <TextWidget text="Ingresos del mes" style={{ fontSize: 10, color: c.muted }} />
          <TextWidget text={data.monthIncome} style={{ fontSize: 14, fontWeight: 'bold', color: c.income }} maxLines={1} />
        </FlexWidget>
        <FlexWidget style={{ flexDirection: 'column', alignItems: 'flex-end' }}>
          <TextWidget text="Gastos del mes" style={{ fontSize: 10, color: c.muted }} />
          <TextWidget text={data.monthExpenses} style={{ fontSize: 14, fontWeight: 'bold', color: c.expense }} maxLines={1} />
        </FlexWidget>
      </FlexWidget>

      <TextWidget
        text={data.nextPayment ? `Próximo pago: ${data.nextPayment}` : `Actualizado ${data.updatedAt}`}
        style={{ fontSize: 10, color: c.muted }}
        maxLines={1}
        truncate="END"
      />
    </FlexWidget>
  );
}
