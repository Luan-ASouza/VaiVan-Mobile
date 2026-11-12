// Envio das notificações push (FCM).
//
// O app desenha a notificação (VaiVanMessagingService → NotificadorVaiVan),
// por isso a mensagem leva só "dados" — todos os valores precisam ser texto.
// Campos entendidos pelo app:
//   titulo, texto   conteúdo da notificação
//   categoria       viagem | mensagens | cadastro | cobranca (canal do Android)
//   grupo           etiqueta: mesma etiqueta = substitui a notificação anterior
//   fixa            "true" = cartão fixo (não some ao tocar nem ao arrastar no Android ≤ 13)
//   silenciosa      "true" = atualiza sem tocar som/vibrar de novo
//   acao            "exibir" (padrão) | "encerrar" (remove a notificação do grupo)
//   destino         aba que o toque abre: rotas | chat | documentos | solicitacoes
//   + ids extras (viagemId, passageiroId, chatId...)

const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");

// Erros que significam "este token não existe mais": o registro é apagado.
const ERROS_TOKEN_INVALIDO = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument"
]);

/** Envia a mensagem para todos os aparelhos do usuário. */
async function enviarPara(uid, dados) {
  if (!uid) return;

  const db = getFirestore();
  const tokensRef = db.collection("dispositivos").doc(uid).collection("tokens");
  const snapshot = await tokensRef.get();
  const tokens = snapshot.docs.map((d) => d.id);
  if (tokens.length === 0) {
    logger.info("Usuário sem aparelho registrado", { uid });
    return;
  }

  const data = {};
  for (const [chave, valor] of Object.entries(dados)) {
    if (valor !== undefined && valor !== null) data[chave] = String(valor);
  }

  const resposta = await getMessaging().sendEachForMulticast({
    tokens,
    data,
    android: { priority: "high" }
  });

  const invalidos = [];
  resposta.responses.forEach((r, i) => {
    if (!r.success && ERROS_TOKEN_INVALIDO.has(r.error?.code)) invalidos.push(tokens[i]);
  });
  await Promise.all(invalidos.map((t) => tokensRef.doc(t).delete()));

  logger.info("Push enviado", {
    uid,
    grupo: data.grupo,
    acao: data.acao ?? "exibir",
    sucesso: resposta.successCount,
    falha: resposta.failureCount,
    removidos: invalidos.length
  });
}

/**
 * Garante que um aviso só sai uma vez: cria notificacoes_enviadas/{chave}
 * e devolve false se ela já existia.
 */
async function primeiraVez(chave) {
  try {
    await getFirestore()
      .collection("notificacoes_enviadas")
      .doc(chave)
      .create({ enviadoEm: Date.now() });
    return true;
  } catch (e) {
    if (e.code === 6) return false; // ALREADY_EXISTS
    throw e;
  }
}

module.exports = { enviarPara, primeiraVez };
