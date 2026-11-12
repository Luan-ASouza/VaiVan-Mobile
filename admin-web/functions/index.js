// Cloud Functions do VaiVan: observam o que o app e o painel já gravam no
// Firestore e enviam a notificação push correspondente (ver enviar.js para o
// formato da mensagem). Nenhuma delas grava dado de negócio.

const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");

const { enviarPara, primeiraVez } = require("./enviar");

initializeApp();

// Mesma região do banco do Firestore (gatilho na mesma região = menor latência).
setGlobalOptions({ region: "southamerica-east1", maxInstances: 10 });

const db = () => getFirestore();

// Mesmos valores de ViagemRepository (app).
const NAO_EMBARCOU = "NAO_EMBARCOU";
const DESEMBARCADO = "DESEMBARCADO";
const LOCAL_ESCOLA = "escola";

// ===================================================================
// VIAGEM (eventos_viagem → responsável)
// ===================================================================

/** Etiqueta do cartão fixo de um aluno numa viagem. */
const grupoViagem = (viagemId, passageiroId) => `viagem_${viagemId}_${passageiroId}`;

function primeiroNome(nome) {
  return (nome || "Seu filho").trim().split(/\s+/)[0];
}

/** Paradas de um aluno na viagem (embarque e desembarque). */
async function paradasDoAluno(viagemId, passageiroId) {
  const snap = await db()
    .collection("paradas_viagem")
    .where("viagemId", "==", viagemId)
    .where("passageiroId", "==", passageiroId)
    .get();
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

/** Falta avisada pelo responsável ou "não embarcou" marcado pelo motorista. */
const faltou = (paradas) =>
  paradas.some((p) => p.tipo === "EMBARQUE" && p.status === NAO_EMBARCOU);

/** Monta a notificação de um evento de parada; null = não notificar. */
function mensagemDaParada(tipoEvento, parada) {
  const nome = primeiroNome(parada.nomePassageiro);
  const naEscola = parada.localId === LOCAL_ESCOLA;
  const ehEmbarque = parada.tipo === "EMBARQUE";

  switch (tipoEvento) {
    case "APROXIMACAO":
      if (naEscola) return null;
      return {
        titulo: "A van está chegando",
        texto: ehEmbarque
          ? `Cerca de 5 min para buscar ${nome}. Prepare-se!`
          : `${nome} chega em casa em cerca de 5 min.`,
        fixa: true
      };
    case "CHEGADA":
      if (naEscola) {
        return {
          titulo: "A van chegou na escola",
          texto: ehEmbarque ? `O motorista está buscando ${nome}.` : `${nome} está descendo na escola.`,
          fixa: true,
          silenciosa: true
        };
      }
      return {
        titulo: "O motorista chegou!",
        texto: ehEmbarque ? `A van está esperando ${nome} na porta.` : `A van chegou em casa com ${nome}.`,
        fixa: true
      };
    case "EMBARQUE":
      return {
        titulo: `${nome} embarcou`,
        texto: naEscola ? `${nome} entrou na van na escola e está voltando para casa.` : `${nome} está na van, a caminho da escola.`,
        fixa: true
      };
    case "DESEMBARQUE":
      // Fim do trajeto do aluno: o aviso substitui o cartão e pode ser descartado.
      return {
        titulo: naEscola ? `${nome} chegou à escola` : `${nome} chegou em casa`,
        texto: naEscola ? `${nome} desceu da van na escola.` : `${nome} desceu da van em casa.`,
        fixa: false
      };
    case NAO_EMBARCOU:
      return {
        titulo: `${nome} não embarcou`,
        texto: parada.motivoAusencia
          ? `O motorista registrou: ${parada.motivoAusencia}`
          : "O motorista passou e registrou que o aluno não embarcou.",
        fixa: false
      };
    default:
      return null;
  }
}

exports.notificarEventoViagem = onDocumentCreated("eventos_viagem/{eventoId}", async (event) => {
  const evento = event.data?.data();
  if (!evento?.viagemId) return;

  if (evento.paradaViagemId) {
    await notificarEventoDeParada(evento);
  } else if (evento.tipo === "INICIO" || evento.tipo === "FINALIZADA") {
    await notificarEventoDaViagem(evento);
  }
});

async function notificarEventoDeParada(evento) {
  const paradaSnap = await db().collection("paradas_viagem").doc(evento.paradaViagemId).get();
  if (!paradaSnap.exists) return;
  const parada = paradaSnap.data();
  if (!parada.responsavelId || !parada.passageiroId) return;

  // Aluno que faltou não tem viagem para acompanhar (só o próprio aviso de NAO_EMBARCOU).
  if (evento.tipo !== NAO_EMBARCOU) {
    const paradas = await paradasDoAluno(evento.viagemId, parada.passageiroId);
    if (faltou(paradas)) return;
  }

  // CHEGADA pode ser gravada pelo motorista e pelo responsável; avisa uma vez só.
  if (evento.tipo === "CHEGADA" || evento.tipo === "APROXIMACAO") {
    if (!(await primeiraVez(`${evento.paradaViagemId}_${evento.tipo}`))) return;
  }

  const msg = mensagemDaParada(evento.tipo, parada);
  if (!msg) return;

  await enviarPara(parada.responsavelId, {
    titulo: msg.titulo,
    texto: msg.texto,
    categoria: "viagem",
    grupo: grupoViagem(evento.viagemId, parada.passageiroId),
    fixa: msg.fixa,
    silenciosa: msg.silenciosa ?? false,
    acao: "exibir",
    destino: "rotas",
    perfil: "responsavel",
    tipo: evento.tipo,
    viagemId: evento.viagemId,
    passageiroId: parada.passageiroId
  });
}

async function notificarEventoDaViagem(evento) {
  const viagemSnap = await db().collection("viagens").doc(evento.viagemId).get();
  if (!viagemSnap.exists) return;
  const viagem = viagemSnap.data();

  const snap = await db().collection("paradas_viagem").where("viagemId", "==", evento.viagemId).get();

  // Agrupa as paradas por aluno
  const porAluno = new Map();
  snap.docs.forEach((d) => {
    const p = d.data();
    if (!p.passageiroId || !p.responsavelId) return;
    if (!porAluno.has(p.passageiroId)) porAluno.set(p.passageiroId, []);
    porAluno.get(p.passageiroId).push(p);
  });

  const envios = [];
  for (const [passageiroId, paradas] of porAluno) {
    if (faltou(paradas)) continue;

    const { responsavelId, nomePassageiro } = paradas[0];
    const nome = primeiroNome(nomePassageiro);
    const grupo = grupoViagem(evento.viagemId, passageiroId);

    if (evento.tipo === "INICIO") {
      const ida = viagem.direcao !== "VOLTA";
      envios.push(
        enviarPara(responsavelId, {
          titulo: "A van está a caminho",
          texto: ida
            ? `A viagem de ida começou. O motorista vai buscar ${nome}.`
            : `A viagem de volta começou. O motorista vai buscar ${nome} na escola.`,
          categoria: "viagem",
          grupo,
          fixa: true,
          silenciosa: false,
          acao: "exibir",
          destino: "rotas",
          perfil: "responsavel",
          tipo: "INICIO",
          viagemId: evento.viagemId,
          passageiroId
        })
      );
    } else {
      // FINALIZADA: quem já desembarcou recebeu o aviso final; só sobra
      // limpar o cartão de quem ficou sem desembarque confirmado.
      const terminou = paradas.every((p) => p.status === DESEMBARCADO || p.status === NAO_EMBARCOU);
      if (terminou) continue;
      envios.push(enviarPara(responsavelId, { grupo, acao: "encerrar", categoria: "viagem" }));
    }
  }

  await Promise.all(envios);
}

// ===================================================================
// CADASTRO DO MOTORISTA (painel admin → motorista)
// ===================================================================

const ROTULOS_DOCUMENTO = {
  CNH: "CNH",
  COMPROVANTE_RESIDENCIA: "Comprovante de residência",
  SELFIE: "Foto do motorista"
};

exports.notificarDocumentoRejeitado = onDocumentUpdated("documentos/{documentoId}", async (event) => {
  const antes = event.data?.before.data();
  const depois = event.data?.after.data();
  if (!antes || !depois?.motoristaId) return;
  if (antes.statusValidacao === depois.statusValidacao) return;
  // Aprovação é avisada uma vez só, quando o motorista vira ATIVO (abaixo).
  if (depois.statusValidacao !== "REJEITADO") return;

  const rotulo = ROTULOS_DOCUMENTO[depois.tipo] ?? "Documento";
  await enviarPara(depois.motoristaId, {
    titulo: `${rotulo} recusado`,
    texto: depois.motivoRejeicao
      ? `Motivo: ${depois.motivoRejeicao}. Envie o documento novamente.`
      : "Envie o documento novamente pelo app.",
    categoria: "cadastro",
    grupo: `documento_${event.params.documentoId}`,
    fixa: false,
    acao: "exibir",
    destino: "documentos",
    perfil: "motorista"
  });
});

exports.notificarStatusMotorista = onDocumentUpdated("usuarios/{uid}", async (event) => {
  const antes = event.data?.before.data()?.statusMotorista ?? null;
  const depois = event.data?.after.data()?.statusMotorista ?? null;
  if (antes === depois) return;

  let msg = null;
  if (depois === "ATIVO") {
    msg = {
      titulo: "Documentos aprovados!",
      texto: "Sua conta de motorista está ativa. Você já pode usar o VaiVan."
    };
  } else if (depois === "SUSPENSO") {
    msg = {
      titulo: "Conta suspensa",
      texto: "Sua conta de motorista foi suspensa. Entre no app para mais detalhes."
    };
  }
  if (!msg) return;

  await enviarPara(event.params.uid, {
    ...msg,
    categoria: "cadastro",
    grupo: "status_motorista",
    fixa: false,
    acao: "exibir",
    destino: "documentos",
    perfil: "motorista"
  });
});

// ===================================================================
// SOLICITAÇÕES DE VAGA
// ===================================================================

exports.notificarNovaSolicitacao = onDocumentCreated("solicitacoes_inclusao/{id}", async (event) => {
  const s = event.data?.data();
  if (!s?.motoristaId || s.status !== "PENDENTE") return;

  await enviarPara(s.motoristaId, {
    titulo: "Nova solicitação de vaga",
    texto: `${s.nomePassageiro || "Um aluno"} quer uma vaga na sua van. Toque para responder.`,
    categoria: "cadastro",
    grupo: `solicitacao_${event.params.id}`,
    fixa: false,
    acao: "exibir",
    destino: "solicitacoes",
    perfil: "motorista",
    solicitacaoId: event.params.id,
    rotaId: s.rotaId
  });
});

exports.notificarRespostaSolicitacao = onDocumentUpdated("solicitacoes_inclusao/{id}", async (event) => {
  const antes = event.data?.before.data();
  const depois = event.data?.after.data();
  if (!antes || !depois?.responsavelId) return;
  if (antes.status === depois.status) return;
  if (depois.status !== "ACEITA" && depois.status !== "RECUSADA") return;

  const nome = primeiroNome(depois.nomePassageiro);
  const aceita = depois.status === "ACEITA";
  await enviarPara(depois.responsavelId, {
    titulo: aceita ? "Vaga aceita!" : "Solicitação recusada",
    texto: aceita
      ? `O motorista aceitou ${nome}. Agora vocês já podem conversar pelo chat.`
      : `O motorista não aceitou a vaga para ${nome}. Procure outra rota no app.`,
    categoria: "cadastro",
    grupo: `solicitacao_${event.params.id}`,
    fixa: false,
    acao: "exibir",
    destino: "solicitacoes",
    perfil: "responsavel",
    solicitacaoId: event.params.id,
    rotaId: depois.rotaId
  });
});

// ===================================================================
// CHAT
// ===================================================================

exports.notificarMensagemChat = onDocumentCreated("chats/{chatId}/mensagens/{mensagemId}", async (event) => {
  const mensagem = event.data?.data();
  if (!mensagem?.remetenteId) return;

  const chatSnap = await db().collection("chats").doc(event.params.chatId).get();
  const chat = chatSnap.data() ?? {};
  const participantes = chat.participantes ?? [];
  const destinatarios = participantes.filter((uid) => uid !== mensagem.remetenteId);
  if (destinatarios.length === 0) return;

  const remetente = await db().collection("usuarios").doc(mensagem.remetenteId).get();
  const nome = remetente.data()?.nome || "Nova mensagem";

  // Todo chat nasce de uma solicitação aceita: ela diz quem é o motorista
  // (o app precisa saber em qual Home abrir a conversa).
  let motoristaId = null;
  if (chat.solicitacaoId) {
    const solicitacao = await db().collection("solicitacoes_inclusao").doc(chat.solicitacaoId).get();
    motoristaId = solicitacao.data()?.motoristaId ?? null;
  }

  await Promise.all(
    destinatarios.map((uid) =>
      enviarPara(uid, {
        titulo: nome,
        texto: mensagem.texto || "Enviou uma mensagem.",
        categoria: "mensagens",
        grupo: `chat_${event.params.chatId}`,
        fixa: false,
        acao: "exibir",
        destino: "chat",
        perfil: uid === motoristaId ? "motorista" : "responsavel",
        chatId: event.params.chatId,
        outroUsuarioId: mensagem.remetenteId,
        nomeContato: nome
      })
    )
  );
});
