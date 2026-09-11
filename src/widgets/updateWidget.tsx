import React from 'react';
import { isWidgetSupported, SUMMARY_WIDGET_NAME } from './widgetSupport';
import { getWidgetSummary } from './widgetData';

/** Redibuja el widget de resumen si está en la pantalla de inicio. No hace nada en Expo Go ni en iOS. */
export async function updateSummaryWidget(): Promise<void> {
  if (!isWidgetSupported) return;

  const { getWidgetInfo, requestWidgetUpdate } =
    require('react-native-android-widget') as typeof import('react-native-android-widget');
  const { SummaryWidget } = require('./SummaryWidget') as typeof import('./SummaryWidget');

  const widgets = await getWidgetInfo(SUMMARY_WIDGET_NAME);
  if (widgets.length === 0) return;

  const data = await getWidgetSummary();
  await requestWidgetUpdate({
    widgetName: SUMMARY_WIDGET_NAME,
    renderWidget: () => ({
      light: <SummaryWidget data={data} theme="light" />,
      dark: <SummaryWidget data={data} theme="dark" />,
    }),
  });
}
