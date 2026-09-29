import { useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { Animated, LayoutAnimation, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { ChevronIcon } from './icons/ChevronIcon';
import { QColors } from '../colors';
import React from 'react';


type AccordionItemProps = PropsWithChildren<{
  title: string;
  expanded: boolean;
  onHeaderPress: () => void;
}>;

export type AccordionData = PropsWithChildren<{
  title: string;
  content: JSX.Element;
}>;

type AccordionProps = {
  data: AccordionData[];
};

function AccordionItem({ children, title, expanded, onHeaderPress }: AccordionItemProps): JSX.Element {
  const rotation = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(rotation, {
      toValue: expanded ? 1 : 0,
      duration: 200,
      useNativeDriver: true,
    }).start();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded]);

  const rotateInterpolate = rotation.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '180deg'],
  });

  const chevronStyle = {
    transform: [{ rotate: rotateInterpolate }],
  };

  const body = <View style={styles.accordBody}>{children}</View>;

  return (
    <View style={styles.accordContainer}>
      <TouchableOpacity style={styles.accordHeader} onPress={onHeaderPress}>
        <Text style={styles.accordTitle}>{title}</Text>
        <Animated.View style={chevronStyle}>
          <ChevronIcon color={QColors.White} size={24} />
        </Animated.View>
      </TouchableOpacity>
      {expanded && body}
    </View>
  );
}

export const Accordion: React.FC<AccordionProps> = ({ data }): JSX.Element => {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  const handleHeaderPress = (index: number) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpandedIndex(expandedIndex === index ? null : index);
  };

  return(
    <>
      {data.map((item, index) => (
        <AccordionItem
          key={index}
          title={item.title}
          expanded={expandedIndex === index}
          onHeaderPress={() => handleHeaderPress(index)}
        >
          {item.content}
        </AccordionItem>
      ))}
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  accordContainer: {
    paddingBottom: 4,
  },
  accordHeader: {
    padding: 12,
    backgroundColor: QColors.Gray,
    flex: 1,
    flexDirection: 'row',
    justifyContent:'space-between',
  },
  accordTitle: {
    fontSize: 20,
    color: QColors.White,
  },
  accordBody: {
    padding: 12,
  },
  textSmall: {
    fontSize: 16,
  },
  seperator: {
    height: 12,
  },
});
