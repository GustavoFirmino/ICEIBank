// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Partes D e F (modelos do app)
// OFFSET pessoal (2 ultimos digitos da matricula): 47

/// Conta como a agencia a devolve em GET /contas/{id}: saldo + vetor atual da agencia.
/// O app guarda o [vetorAtual] junto do saldo no cache local: e o "vetor conhecido" que ele
/// devolve ao sincronizar uma operacao feita offline (Parte E).
class Conta {
  const Conta({required this.id, required this.titular, required this.saldo, this.vetorAtual = const []});

  final int id;
  final String titular;
  final int saldo;
  final List<int> vetorAtual;

  factory Conta.fromJson(Map<String, dynamic> j) => Conta(
        id: (j['id'] as num).toInt(),
        titular: j['titular'] as String? ?? '',
        saldo: (j['saldo'] as num).toInt(),
        vetorAtual: ((j['vetorAtual'] as List?) ?? const []).map((e) => (e as num).toInt()).toList(),
      );

  Map<String, dynamic> toJson() => {'id': id, 'titular': titular, 'saldo': saldo, 'vetorAtual': vetorAtual};

  Conta copyWith({int? saldo, List<int>? vetorAtual}) =>
      Conta(id: id, titular: titular, saldo: saldo ?? this.saldo, vetorAtual: vetorAtual ?? this.vetorAtual);
}

enum TipoOperacao { deposito, saque }

extension TipoOperacaoApi on TipoOperacao {
  /// Valor esperado por POST /contas/{id}/sincronizar.
  String get api => this == TipoOperacao.deposito ? 'DEPOSITO' : 'SAQUE';
  String get rotulo => this == TipoOperacao.deposito ? 'Depósito' : 'Saque';
}

/// pendente  = aguardando conexao/sincronizacao (UI otimista: o saldo provisorio ja a considera);
/// conflito  = a agencia recusou com 409 (o estado da conta mudou enquanto o app estava offline);
/// rejeitada = a agencia recusou por outro motivo (ex.: saldo insuficiente de fato).
enum EstadoOperacao { pendente, conflito, rejeitada }

class OperacaoPendente {
  OperacaoPendente({
    required this.id,
    required this.contaId,
    required this.tipo,
    required this.valor,
    required this.vetorConhecido,
    required this.criadaEm,
    this.saldoConhecido,
    this.estado = EstadoOperacao.pendente,
    this.mensagem,
    this.contaServidor,
  });

  final String id;
  final int contaId;
  final TipoOperacao tipo;
  final int valor;

  /// Vetor da agencia na ultima vez em que o app leu a conta.
  List<int> vetorConhecido;
  final DateTime criadaEm;

  /// Ultimo saldo confirmado pela agencia que o app tinha ao registrar a operacao (base da decisao da pessoa).
  final int? saldoConhecido;
  EstadoOperacao estado;
  String? mensagem;

  /// Estado real da conta devolvido no 409 (para mostrar a pessoa o que mudou).
  Conta? contaServidor;

  /// Efeito da operacao no saldo (usado no saldo provisorio).
  int get efeito => tipo == TipoOperacao.deposito ? valor : -valor;

  Map<String, dynamic> toJson() => {
        'id': id,
        'contaId': contaId,
        'tipo': tipo.name,
        'valor': valor,
        'vetorConhecido': vetorConhecido,
        'criadaEm': criadaEm.toIso8601String(),
        'saldoConhecido': saldoConhecido,
        'estado': estado.name,
        'mensagem': mensagem,
        'contaServidor': contaServidor?.toJson(),
      };

  factory OperacaoPendente.fromJson(Map<String, dynamic> j) => OperacaoPendente(
        id: j['id'] as String,
        contaId: (j['contaId'] as num).toInt(),
        tipo: TipoOperacao.values.byName(j['tipo'] as String),
        valor: (j['valor'] as num).toInt(),
        vetorConhecido: (j['vetorConhecido'] as List).map((e) => (e as num).toInt()).toList(),
        criadaEm: DateTime.parse(j['criadaEm'] as String),
        saldoConhecido: (j['saldoConhecido'] as num?)?.toInt(),
        estado: EstadoOperacao.values.byName(j['estado'] as String),
        mensagem: j['mensagem'] as String?,
        contaServidor: j['contaServidor'] == null
            ? null
            : Conta.fromJson((j['contaServidor'] as Map).cast<String, dynamic>()),
      );
}
