import { SvgIconProps } from '../types/SvgIconProps';
import Svg, { Path } from 'react-native-svg';
import { initLlama, loadLlamaModelInfo } from 'llama.rn';
import { ReactNode, useRef, useState } from 'react';
import { Chat, darkTheme, MessageType } from '@flyerhq/react-native-chat-ui';
import { Bubble } from '../widgets/Bubble';
import json5 from 'json5';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { llmModels } from '../models';
import { calculateSha256, getLocalPath, scanForModels } from '../fsUtils';
import Modal from 'react-native-modal';
import { FlatList, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { LlmModel } from '../types/LlmModel';
import { PerformanceWidget } from '../widgets/PerformanceWidget';
import { QColors } from '../colors';
import { useLlamaContext } from '../contexts/llamaContext';
import { useIsFocused } from '@react-navigation/native';

export const InferenceScreenIcon: React.FC<SvgIconProps> = ({ color, size }) => (
  <Svg width={size} height={size} viewBox="0 0 24 24">
    <Path fill={color} d="M4 18h2v4.081L11.101 18H16c1.103 0 2-.897 2-2V8c0-1.103-.897-2-2-2H4c-1.103 0-2 .897-2 2v8c0 1.103.897 2 2 2z" />
    <Path fill={color} d="M20 2H8c-1.103 0-2 .897-2 2h12c1.103 0 2 .897 2 2v8c1.103 0 2-.897 2-2V4c0-1.103-.897-2-2-2z" />
  </Svg>
);

const randId = () => Math.random().toString(36).substring(2, 9);

const user = { id: 'y9d7f8pgn' };

const systemId = 'h3o3lc5xj';
const system = { id: systemId };

const systemMessage = {
  role: 'system',
  content:
    'This is a conversation between user and assistant, a friendly chatbot.\n\n',
};

const renderBubble = ({
  child,
  message,
}: {
  child: ReactNode
  message: MessageType.Any
}) => <Bubble child={child} message={message} />;

function InferenceScreen() {
  const isFocused = useIsFocused();
  const { context, setContext } = useLlamaContext();
  const [models, setModels] = useState<LlmModel[]>([]);
  const [inferencing, setInferencing] = useState<boolean>(false);
  const [messages, setMessages] = useState<MessageType.Any[]>([]);
  const [isModelPickerVisible, setModelPickerVisible] = useState<boolean>(false);
  const conversationIdRef = useRef<string>('default');

  const addMessage = (message: MessageType.Any, batching = false) => {
    if (batching) {
      setMessages([message, ...messages]);
    } else {
      setMessages((msgs) => [message, ...msgs]);
    }
  };

  const handlePickModel = async () => {
    const _models = await scanForModels(llmModels);
    setModels(_models);
    if (_models.length === 0) {
      addSystemMessage('No models found.');
      return;
    }
    setModelPickerVisible(true);
  };

  const handleModelSelect = async (model: LlmModel) => {
    setModelPickerVisible(false);
    addSystemMessage(`Selected model: ${model.name}`);
    const modelPath = getLocalPath(model.filename);

    const sha256 = await calculateSha256(modelPath);
    if (sha256 !== model.sha256) {
      addSystemMessage(
        `Model file checksum mismatch!\n\nExpected: ${model.sha256}\nActual: ${sha256}`,
        { copyable: true },
      );
      return;
    }

    await handleInitContext(modelPath);
  };

  const addSystemMessage = (text: string, metadata = {}) => {
    const textMessage: MessageType.Text = {
      author: system,
      createdAt: Date.now(),
      id: randId(),
      text,
      type: 'text',
      metadata: { system: true, ...metadata },
    };
    addMessage(textMessage);
    return textMessage.id;
  };

  const handleReleaseContext = async () => {
    if (!context) return;
    addSystemMessage('Releasing context...');
    context
      .release()
      .then(() => {
        setContext(undefined);
        addSystemMessage('Context released!');
      })
      .catch((err) => {
        addSystemMessage(`Context release failed: ${err}`);
      });
  };

  const getModelInfo = async (model: string) => {
    const t0 = Date.now();
    const info = await loadLlamaModelInfo(model);
    console.log(`Model info (took ${Date.now() - t0}ms): `, info);
    if (!info) {
      throw new Error('Model info could not be loaded');
    }
  };

  const handleInitContext = async (
    file: string,
  ) => {
    try {
      await handleReleaseContext();
      await getModelInfo(file);
    }
    catch (e: any) {
      addSystemMessage(`Failed to load model. Message: ${e.message}`);
      return;
    }
    const msgId = addSystemMessage('Initializing context...');
    const t0 = Date.now();
    initLlama(
      {
        model: file,
        use_mlock: true,
        n_gpu_layers: 0,
        n_ctx: 4096,
        // embedding: true,
      },
      (progress) => {
        setMessages((msgs) => {
          const index = msgs.findIndex((msg) => msg.id === msgId);
          if (index >= 0) {
            return msgs.map((msg, i) => {
              if (msg.type === 'text' && i === index) {
                return {
                  ...msg,
                  text: `Initializing context... ${progress}%`,
                };
              }
              return msg;
            });
          }
          return msgs;
        });
      },
    )
      .then((ctx) => {
        const t1 = Date.now();
        setContext(ctx);
        addSystemMessage(
          `Context initialized!\n\nLoad time: ${t1 - t0}ms\n` +
            'You can use the following commands:\n\n' +
            '- /info: to get the model info\n' +
            '- /release: release the context\n' +
            '- /stop: stop the current completion\n' +
            '- /reset: reset the conversation'
        );
      })
      .catch((err) => {
        addSystemMessage(`Context initialization failed: ${err.message}`);
      });
  };

  const handleSendPress = async (message: MessageType.PartialText) => {
    if (context) {
      switch (message.text) {
        case '/info':
          addSystemMessage(
            `// Model Info\n${json5.stringify(context.model, null, 2)}`,
            { copyable: true },
          );
          return;
        case '/release':
          await handleReleaseContext();
          return;
        case '/stop':
          if (inferencing) context.stopCompletion();
          return;
        case '/reset':
          conversationIdRef.current = randId();
          addSystemMessage('Conversation reset!');
          return;
      }
    }
    const textMessage: MessageType.Text = {
      author: user,
      createdAt: Date.now(),
      id: randId(),
      text: message.text,
      type: 'text',
      metadata: {
        contextId: context?.id,
        conversationId: conversationIdRef.current,
      },
    };

    const id = randId();
    const createdAt = Date.now();
    const msgs = [
      systemMessage,
      ...[...messages]
        .reverse()
        .map((msg) => {
          if (
            !msg.metadata?.system &&
            msg.metadata?.conversationId === conversationIdRef.current &&
            msg.metadata?.contextId === context?.id &&
            msg.type === 'text'
          ) {
            return {
              role: msg.author.id === systemId ? 'assistant' : 'user',
              content: msg.text,
            };
          }
          return { role: '', content: '' };
        })
        .filter((msg) => msg.role),
      { role: 'user', content: message.text },
    ];
    addMessage(textMessage);
    setInferencing(true);

    let grammar;

    context
      ?.completion(
        {
          messages: msgs,
          n_predict: 1000,
          grammar,
          seed: -1,
          n_probs: 0,

          // Sampling params
          top_k: 40,
          top_p: 0.5,
          min_p: 0.05,
          xtc_probability: 0.5,
          xtc_threshold: 0.1,
          typical_p: 1.0,
          temperature: 0.7,
          penalty_last_n: 64,
          penalty_repeat: 1.0,
          penalty_freq: 0.0,
          penalty_present: 0.0,
          dry_multiplier: 0,
          dry_base: 1.75,
          dry_allowed_length: 2,
          dry_penalty_last_n: -1,
          dry_sequence_breakers: ['\n', ':', '"', '*'],
          mirostat: 0,
          mirostat_tau: 5,
          mirostat_eta: 0.1,
          ignore_eos: false,
          stop: [
            '</s>',
            '<|end|>',
            '<|eot_id|>',
            '<|end_of_text|>',
            '<|im_end|>',
            '<|EOT|>',
            '<|END_OF_TURN_TOKEN|>',
            '<|end_of_turn|>',
            '<|endoftext|>',
            '<end_of_turn>',
            '<eos>',
          ],
          // n_threads: 4,
          // logit_bias: [[15043,1.0]],
        },
        (data) => {
          const { token } = data;
          setMessages((msgs) => {
            const index = msgs.findIndex((msg) => msg.id === id);
            if (index >= 0) {
              return msgs.map((msg, i) => {
                if (msg.type === 'text' && i === index) {
                  return {
                    ...msg,
                    text: (msg.text + token).replace(/^\s+/, ''),
                  };
                }
                return msg;
              });
            }
            return [
              {
                author: system,
                createdAt,
                id,
                text: token,
                type: 'text',
                metadata: {
                  contextId: context?.id,
                  conversationId: conversationIdRef.current,
                },
              },
              ...msgs,
            ];
          });
        },
      )
      .then((completionResult) => {
        console.log('completionResult: ', completionResult);
        const timings = `${completionResult.timings.predicted_per_token_ms.toFixed()}ms per token, ${completionResult.timings.predicted_per_second.toFixed(
          2,
        )} tokens per second`;
        setMessages((msgs) => {
          const index = msgs.findIndex((msg) => msg.id === id);
          if (index >= 0) {
            return msgs.map((msg, i) => {
              if (msg.type === 'text' && i === index) {
                return {
                  ...msg,
                  metadata: {
                    ...msg.metadata,
                    timings,
                  },
                };
              }
              return msg;
            });
          }
          return msgs;
        });
        setInferencing(false);
      })
      .catch((e) => {
        console.log('completion error: ', e);
        setInferencing(false);
        addSystemMessage(`Completion failed: ${e.message}`);
      });
  };

  return (
    <SafeAreaProvider>
      <PerformanceWidget isActive={isFocused} />
      <Chat
        renderBubble={renderBubble}
        theme={{
          ...darkTheme,
          colors: { ...darkTheme.colors, background: QColors.Gray, inputBackground: QColors.Gray, primary: QColors.LightBlue },
        }}
        messages={messages}
        onSendPress={handleSendPress}
        onAttachmentPress={!context ? handlePickModel : undefined}
        user={user}
        textInputProps={{
          editable: !!context,
          placeholder: !context
            ? 'Press the file icon to pick a model'
            : 'Type your message here',
        }}
      />
      <Modal
        isVisible={isModelPickerVisible}
        onBackdropPress={() => setModelPickerVisible(false)}
      >
        <View style={styles.modalContainer}>
          <Text style={styles.modalTitle}>Select a Model</Text>
          <FlatList
            data={models}
            keyExtractor={(item) => item.filename}
            renderItem={({ item }) => (
              <TouchableOpacity
                style={styles.modelItem}
                onPress={() => handleModelSelect(item)}
              >
                <Text style={styles.modelName}>{item.name}</Text>
              </TouchableOpacity>
            )}
          />
        </View>
      </Modal>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  modalContainer: {
    backgroundColor: 'white',
    borderRadius: 10,
    padding: 20,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  modelItem: {
    padding: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#ddd',
  },
  modelName: {
    fontSize: 16,
  },
});

export default InferenceScreen;
