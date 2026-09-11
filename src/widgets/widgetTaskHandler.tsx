import React from 'react';
import type { WidgetTaskHandlerProps } from 'react-native-android-widget';
import { SummaryWidget } from './SummaryWidget';
import { getWidgetSummary } from './widgetData';
import { SUMMARY_WIDGET_NAME } from './widgetSupport';

/** Task headless que Android invoca al agregar, actualizar (cada 30 min) o redimensionar el widget. */
export async function widgetTaskHandler(props: WidgetTaskHandlerProps) {
  if (props.widgetInfo.widgetName !== SUMMARY_WIDGET_NAME) return;

  switch (props.widgetAction) {
    case 'WIDGET_ADDED':
    case 'WIDGET_UPDATE':
    case 'WIDGET_RESIZED': {
      const data = await getWidgetSummary();
      props.renderWidget({
        light: <SummaryWidget data={data} theme="light" />,
        dark: <SummaryWidget data={data} theme="dark" />,
      });
      break;
    }
    default:
      // WIDGET_DELETED no guarda estado propio y WIDGET_CLICK se resuelve con OPEN_APP.
      break;
  }
}
