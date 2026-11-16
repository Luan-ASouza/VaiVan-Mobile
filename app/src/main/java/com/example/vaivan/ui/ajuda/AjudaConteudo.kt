package com.example.vaivan.ui.ajuda

/**
 * BASE DE CONHECIMENTO DO APP
 *
 * Todos os manuais ficam neste arquivo. Para adicionar ou corrigir um guia,
 * é só editar a lista [AjudaConteudo.guias] abaixo.
 *
 * Cada guia tem:
 *  - id: nome único (sem espaços), usado para abrir o guia
 *  - titulo / resumo: aparecem na lista da tela de Ajuda
 *  - perfis: quem vê o guia (responsável, motorista ou os dois)
 *  - passos: lista de passos, na ordem
 *  - dica: texto opcional que aparece no final do guia
 */

enum class PerfilAjuda(val rotulo: String) {
    RESPONSAVEL("Responsável"),
    MOTORISTA("Motorista")
}

data class GuiaAjuda(
    val id: String,
    val titulo: String,
    val resumo: String,
    val perfis: Set<PerfilAjuda>,
    val passos: List<String>,
    val dica: String? = null
)

object AjudaConteudo {

    private val responsavel = setOf(PerfilAjuda.RESPONSAVEL)
    private val motorista = setOf(PerfilAjuda.MOTORISTA)
    private val ambos = setOf(PerfilAjuda.RESPONSAVEL, PerfilAjuda.MOTORISTA)

    val guias: List<GuiaAjuda> = listOf(

        // =====================================================
        // RESPONSÁVEL
        // =====================================================

        GuiaAjuda(
            id = "cadastrar-filho",
            titulo = "Como cadastrar meu filho para localizar uma van",
            resumo = "Cadastre os dados do seu filho e o local de embarque para poder buscar vans.",
            perfis = responsavel,
            passos = listOf(
                "Entre no app como responsável e abra a aba Passageiros.",
                "Toque na opção para adicionar um novo passageiro.",
                "Preencha os dados do seu filho, como o nome, e adicione uma foto se quiser.",
                "Selecione a escola onde ele estuda.",
                "Cadastre o local de embarque (o endereço de onde a van vai buscá-lo).",
                "Salve. O seu filho aparece na lista de passageiros e já pode ser usado para buscar uma van."
            ),
            dica = "Você pode cadastrar mais de um filho. Cada um tem a sua própria viagem e a sua própria conversa com o motorista."
        ),

        GuiaAjuda(
            id = "encontrar-motorista",
            titulo = "Como encontrar um motorista que atenda minha rota",
            resumo = "Pesquise rotas perto de casa e da escola e envie um pedido de vaga.",
            perfis = responsavel,
            passos = listOf(
                "Confirme que o seu filho já está cadastrado na aba Passageiros, com o local de embarque.",
                "Abra a área de Rotas e escolha a opção de pesquisar rotas.",
                "Informe o endereço de embarque, a escola de destino, o turno e os dias da semana.",
                "O app mostra as rotas ativas que passam a até 1 km do embarque, terminam perto da escola e ainda têm vaga.",
                "Toque em uma rota para ver os detalhes, como motorista, veículo e vagas disponíveis.",
                "Envie a solicitação de inclusão. Se quiser, escreva uma mensagem para o motorista, por exemplo com o horário desejado.",
                "Acompanhe o pedido na aba Rotas, em Solicitações. Quando o motorista aceitar, a viagem passa a aparecer em Suas viagens."
            ),
            dica = "Se nenhuma rota aparecer, tente mudar o turno, tirar alguns dias da semana ou conferir se o endereço de embarque está correto."
        ),

        GuiaAjuda(
            id = "acompanhar-viagem",
            titulo = "Como acompanhar a viagem do meu filho",
            resumo = "Veja a van no mapa, em tempo real, durante a viagem.",
            perfis = responsavel,
            passos = listOf(
                "Abra a aba Rotas.",
                "Em Suas viagens, encontre o card do seu filho.",
                "Toque em Acompanhar viagem.",
                "Veja no mapa o trajeto da rota e a posição da van quando a viagem estiver em andamento."
            ),
            dica = "A van só aparece no mapa depois que o motorista inicia a viagem. Se ela ainda não apareceu, a viagem provavelmente não começou."
        ),

        GuiaAjuda(
            id = "conversar-motorista",
            titulo = "Como conversar com o motorista",
            resumo = "Envie mensagens para o motorista da rota do seu filho.",
            perfis = responsavel,
            passos = listOf(
                "Abra a aba Rotas.",
                "Em Suas viagens, no card do seu filho, toque em Enviar mensagem.",
                "A conversa abre com o motorista daquela rota. Cada filho e cada rota têm a sua própria conversa.",
                "Escreva a mensagem e envie. As respostas aparecem na mesma tela.",
                "Você também encontra as suas conversas na aba Conversar."
            ),
            dica = "A conversa só fica disponível depois que o motorista aceita a solicitação de vaga."
        ),

        // =====================================================
        // MOTORISTA
        // =====================================================

        GuiaAjuda(
            id = "enviar-documentos",
            titulo = "Como enviar meus documentos",
            resumo = "Envie CNH, comprovante de residência e selfie para validar o cadastro.",
            perfis = motorista,
            passos = listOf(
                "No cadastro como motorista, o app pede alguns documentos para validar a sua conta.",
                "Envie uma foto legível da sua CNH.",
                "Envie o comprovante de residência.",
                "Tire a selfie seguindo as orientações da tela.",
                "Confirme o envio e aguarde a conclusão do cadastro."
            ),
            dica = "Fotografe em um lugar bem iluminado e deixe todas as informações do documento visíveis."
        ),

        GuiaAjuda(
            id = "cadastrar-veiculo",
            titulo = "Como cadastrar meu veículo",
            resumo = "Cadastre a van para poder usá-la nas suas rotas.",
            perfis = motorista,
            passos = listOf(
                "Abra a aba Veículos.",
                "Toque na opção para adicionar um veículo.",
                "Preencha os dados pedidos, como modelo e placa.",
                "Confira as informações e salve.",
                "O veículo aparece na lista e pode ser escolhido ao criar uma rota."
            ),
            dica = "Você pode ver todos os seus veículos pelo menu lateral, em Meus veículos."
        ),

        GuiaAjuda(
            id = "cadastrar-rota",
            titulo = "Como cadastrar uma rota",
            resumo = "Crie uma rota para que os responsáveis possam encontrar a sua van.",
            perfis = motorista,
            passos = listOf(
                "Abra a aba Rotas e toque para criar uma nova rota.",
                "Dê um nome para a rota e escolha o tipo de trajeto: Ida, Volta ou Ida e volta.",
                "Informe o ponto de partida e a escola de destino. O app calcula o trajeto sozinho.",
                "Escolha o turno e os dias da semana em que a rota funciona.",
                "Defina o número de vagas e o veículo da rota.",
                "Salve. A rota fica ativa e passa a aparecer nas buscas dos responsáveis."
            ),
            dica = "Para aparecer nas buscas, a rota precisa estar ativa, ter vagas livres e a sua assinatura precisa estar em dia."
        ),

        GuiaAjuda(
            id = "responder-solicitacoes",
            titulo = "Como aceitar ou recusar solicitações de vaga",
            resumo = "Veja os pedidos dos responsáveis e inclua os alunos na rota.",
            perfis = motorista,
            passos = listOf(
                "Abra a aba Rotas e toque na rota.",
                "Na tela de detalhes, veja a lista de Solicitações: nome do aluno, endereço de embarque, horário desejado e a mensagem do responsável.",
                "Toque em Aceitar para incluir o aluno. O app recalcula a rota com a nova parada.",
                "Ou toque em Recusar, se não puder atender o pedido.",
                "Os alunos aceitos aparecem em Alunos confirmados. Os recusados aparecem em Recusados."
            ),
            dica = "Se a rota estiver sem vagas livres, não é possível aceitar novos alunos."
        ),

        GuiaAjuda(
            id = "conversar-responsavel",
            titulo = "Como conversar com o responsável",
            resumo = "Envie mensagens para o responsável de cada aluno confirmado.",
            perfis = motorista,
            passos = listOf(
                "Abra a aba Rotas e toque na rota.",
                "Em Alunos confirmados, toque em Enviar mensagem no card do aluno.",
                "Escreva a mensagem e envie. Cada aluno tem a sua própria conversa.",
                "Você também encontra as suas conversas na aba de conversas."
            ),
            dica = "Só é possível conversar com os responsáveis dos alunos que você já aceitou."
        ),

        GuiaAjuda(
            id = "iniciar-viagem",
            titulo = "Como iniciar uma viagem",
            resumo = "Comece a ida ou a volta e deixe os responsáveis acompanharem.",
            perfis = motorista,
            passos = listOf(
                "Abra a aba Rotas e toque na rota.",
                "Toque em Iniciar Ida ou Iniciar Volta, conforme o tipo da rota.",
                "Siga a navegação pelas paradas, na ordem mostrada na tela.",
                "Confirme o embarque de cada aluno conforme for buscando.",
                "Os responsáveis acompanham a van no mapa em tempo real."
            ),
            dica = "Para iniciar, a rota precisa estar ativa e ter pelo menos um aluno confirmado. Mantenha o GPS e a internet ligados durante a viagem."
        ),

        // =====================================================
        // MOTORISTA E RESPONSÁVEL
        // =====================================================

        GuiaAjuda(
            id = "editar-perfil",
            titulo = "Como editar meu perfil",
            resumo = "Altere seus dados e sua foto.",
            perfis = ambos,
            passos = listOf(
                "Abra o menu lateral e toque em Meu perfil, ou use a aba Perfil.",
                "Toque em Editar Perfil.",
                "Altere os dados ou a foto.",
                "Salve as alterações."
            )
        ),

        GuiaAjuda(
            id = "trocar-senha",
            titulo = "Como trocar minha senha",
            resumo = "Defina uma nova senha para a sua conta.",
            perfis = ambos,
            passos = listOf(
                "Abra o Perfil e toque em Trocar senha.",
                "Digite a sua senha atual.",
                "Digite a nova senha e confirme.",
                "Toque em Salvar."
            ),
            dica = "A nova senha precisa ter pelo menos 6 caracteres. Se você esqueceu a senha, use a opção de recuperação de senha na tela de login."
        ),

        GuiaAjuda(
            id = "desconectar",
            titulo = "Como sair da minha conta",
            resumo = "Desconecte a sua conta do aparelho.",
            perfis = ambos,
            passos = listOf(
                "Abra o menu lateral e toque em Desconectar. Você também pode ir em Perfil e tocar em Desconectar.",
                "O app volta para a tela inicial, onde é possível entrar de novo ou criar outra conta."
            )
        ),

        GuiaAjuda(
            id = "excluir-conta",
            titulo = "Como excluir minha conta",
            resumo = "Apague a sua conta e os seus dados do app.",
            perfis = ambos,
            passos = listOf(
                "Abra a aba Perfil.",
                "Toque em Excluir minha conta.",
                "Leia o aviso e confirme."
            ),
            dica = "Atenção: a exclusão apaga a conta e não pode ser desfeita."
        )
    )

    /** Guias de um perfil. Sem perfil (tela de login), devolve todos. */
    fun paraPerfil(perfil: PerfilAjuda?): List<GuiaAjuda> =
        if (perfil == null) guias else guias.filter { perfil in it.perfis }

    fun porId(id: String): GuiaAjuda? =
        guias.firstOrNull { it.id == id }
}