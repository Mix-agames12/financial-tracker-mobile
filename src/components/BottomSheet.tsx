import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Animated,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  PanResponder,
  PanResponderGestureState,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';

interface BottomSheetProps {
  visible: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

type Snap = 'half' | 'full';

const HALF_RATIO = 0.6;
const TOP_GAP = 8;
const DISMISS_DISTANCE = 120;
const DISMISS_VELOCITY = 1.5;
const SNAP_DISTANCE = 60;
const SNAP_VELOCITY = 0.5;
const INPUT_MARGIN = 24;

export const BottomSheet: React.FC<BottomSheetProps> = ({ visible, onClose, title, children }) => {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { height: windowHeight } = useWindowDimensions();

  // Altura útil dentro del modal: el KeyboardAvoidingView la reduce cuando el teclado está abierto.
  const [availableHeight, setAvailableHeight] = useState(windowHeight);
  const [snap, setSnap] = useState<Snap>('half');
  const [keyboardVisible, setKeyboardVisible] = useState(false);

  const fullHeight = Math.max(availableHeight - insets.top - TOP_GAP, 0);
  const halfHeight = Math.min(fullHeight, availableHeight * HALF_RATIO);
  // Con el teclado abierto la hoja ocupa todo el espacio libre sobre él.
  const targetHeight = snap === 'full' || keyboardVisible ? fullHeight : halfHeight;

  const maxHeight = useRef(new Animated.Value(halfHeight)).current;
  const dragY = useRef(new Animated.Value(0)).current;
  const scrollRef = useRef<ScrollView>(null);
  const contentRef = useRef<View>(null);
  const scrollState = useRef({ offset: 0, viewport: 0 });

  // El PanResponder se crea una sola vez: lee los valores vigentes desde esta ref.
  const gesture = useRef({ half: halfHeight, full: fullHeight, snap, keyboardVisible, startHeight: halfHeight, onClose });
  gesture.current.half = halfHeight;
  gesture.current.full = fullHeight;
  gesture.current.snap = snap;
  gesture.current.keyboardVisible = keyboardVisible;
  gesture.current.onClose = onClose;

  const animateHeight = useCallback((toValue: number) => {
    Animated.timing(maxHeight, { toValue, duration: 220, useNativeDriver: false }).start();
  }, [maxHeight]);

  const changeSnap = useCallback((next: Snap) => {
    // Bajar a media altura mientras se escribe cierra el teclado.
    if (next === 'half' && gesture.current.keyboardVisible) Keyboard.dismiss();
    setSnap(next);
  }, []);

  /** Desplaza el TextInput enfocado a la zona visible si el teclado o el cambio de altura lo taparon. */
  const scrollFocusedInputIntoView = useCallback(() => {
    const input = TextInput.State.currentlyFocusedInput();
    const content = contentRef.current;
    if (!input || !content) return;
    input.measureLayout(
      content,
      (_x, y, _width, height) => {
        const { offset, viewport } = scrollState.current;
        if (y < offset + INPUT_MARGIN || y + height > offset + viewport - INPUT_MARGIN) {
          scrollRef.current?.scrollTo({ y: Math.max(0, y - INPUT_MARGIN), animated: true });
        }
      },
      () => {}
    );
  }, []);

  // Cada apertura empieza a media altura y sin desplazamiento.
  useEffect(() => {
    if (!visible) return;
    dragY.setValue(0);
    maxHeight.setValue(gesture.current.half);
    setSnap('half');
    setKeyboardVisible(Keyboard.isVisible());
  }, [visible, dragY, maxHeight]);

  useEffect(() => {
    animateHeight(targetHeight);
  }, [targetHeight, animateHeight]);

  useEffect(() => {
    if (!visible) return;
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const showSub = Keyboard.addListener(showEvent, () => {
      setKeyboardVisible(true);
      // Espera a que el KeyboardAvoidingView y la hoja terminen de reacomodarse antes de medir.
      setTimeout(scrollFocusedInputIntoView, 300);
    });
    const hideSub = Keyboard.addListener(hideEvent, () => setKeyboardVisible(false));
    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, [visible, scrollFocusedInputIntoView]);

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_, g) => Math.abs(g.dy) > 5,
      onPanResponderGrant: () => {
        const g = gesture.current;
        g.startHeight = g.snap === 'full' || g.keyboardVisible ? g.full : g.half;
      },
      onPanResponderMove: (_, g) => {
        const { half, full, startHeight } = gesture.current;
        const next = startHeight - g.dy; // arrastrar hacia arriba (dy < 0) agranda la hoja
        if (next >= half) {
          maxHeight.setValue(Math.min(next, full));
          dragY.setValue(0);
        } else {
          // Por debajo de media altura la hoja baja para cerrarse.
          maxHeight.setValue(half);
          dragY.setValue(half - next);
        }
      },
      onPanResponderRelease: (_, g) => releaseGesture(g),
      onPanResponderTerminate: (_, g) => releaseGesture(g),
    })
  ).current;

  function releaseGesture(g: PanResponderGestureState) {
    const { half, full, startHeight, snap: currentSnap, keyboardVisible: kbVisible } = gesture.current;
    const belowHalf = half - (startHeight - g.dy);
    const startedAtHalf = startHeight <= half + 1;

    if (belowHalf > DISMISS_DISTANCE || (startedAtHalf && g.vy > DISMISS_VELOCITY)) {
      Animated.timing(dragY, { toValue: full + 100, duration: 200, useNativeDriver: false })
        .start(() => gesture.current.onClose());
      return;
    }

    Animated.spring(dragY, { toValue: 0, bounciness: 0, useNativeDriver: false }).start();

    let next: Snap | null = null;
    if (g.vy < -SNAP_VELOCITY || g.dy < -SNAP_DISTANCE) next = 'full';
    else if (g.vy > SNAP_VELOCITY || g.dy > SNAP_DISTANCE) next = 'half';

    // Un arrastre corto no cambia el snap: la hoja vuelve a la altura que tenía.
    if (next) changeSnap(next);
    const resolved = next ?? currentSnap;
    animateHeight(resolved === 'full' || (kbVisible && next !== 'half') ? full : half);
  }

  const isFull = snap === 'full' || keyboardVisible;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
      statusBarTranslucent
      navigationBarTranslucent
    >
      {/* Sólo con el teclado visible: en Android, al ocultarse, RN envía un screenY sin las barras del
          sistema y KeyboardAvoidingView dejaba una franja vacía bajo la hoja. */}
      <KeyboardAvoidingView style={styles.flex} behavior="padding" enabled={keyboardVisible}>
        <View style={styles.overlay} onLayout={(e) => setAvailableHeight(e.nativeEvent.layout.height)}>
          <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
          <Animated.View
            style={[
              styles.sheet,
              { backgroundColor: colors.surfaceContainerHigh, maxHeight, transform: [{ translateY: dragY }] },
            ]}
          >
            <View {...panResponder.panHandlers}>
              <TouchableOpacity
                style={styles.handleArea}
                onPress={() => changeSnap(isFull ? 'half' : 'full')}
                hitSlop={{ top: 8, bottom: 8, left: 48, right: 48 }}
                accessibilityRole="button"
                accessibilityLabel={isFull ? 'Contraer' : 'Expandir'}
                accessibilityHint="También puedes arrastrar hacia arriba o hacia abajo"
              >
                <View style={[styles.handle, { backgroundColor: colors.outline }]} />
              </TouchableOpacity>
              <View style={styles.header}>
                <Text style={[styles.title, { color: colors.onSurface }]} numberOfLines={1}>{title}</Text>
                <TouchableOpacity onPress={onClose} style={styles.closeBtn} accessibilityLabel="Cerrar">
                  <Ionicons name="close-circle" size={28} color={colors.onSurfaceVariant} />
                </TouchableOpacity>
              </View>
            </View>

            <ScrollView
              ref={scrollRef}
              contentContainerStyle={{ paddingBottom: keyboardVisible ? 16 : 24 + insets.bottom }}
              keyboardShouldPersistTaps="handled"
              scrollEventThrottle={16}
              onScroll={(e) => { scrollState.current.offset = e.nativeEvent.contentOffset.y; }}
              onLayout={(e) => { scrollState.current.viewport = e.nativeEvent.layout.height; }}
            >
              <View ref={contentRef} collapsable={false}>
                {children}
              </View>
            </ScrollView>
          </Animated.View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'flex-end',
  },
  backdrop: {
    flex: 1,
  },
  sheet: {
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  handleArea: {
    alignSelf: 'center',
    paddingVertical: 6,
    marginBottom: 10,
  },
  handle: {
    width: 32,
    height: 4,
    borderRadius: 2,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  title: {
    flex: 1,
    marginRight: 8,
    fontSize: 20,
    fontWeight: 'bold',
    fontFamily: 'sans-serif',
  },
  closeBtn: {
    padding: 4,
  },
});
