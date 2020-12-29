package io.horizontalsystems.ethereumkit.core.refactoring

import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransaction
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.EtherscanTransactionsProvider
import io.horizontalsystems.ethereumkit.models.NotSyncedTransaction
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.math.BigInteger
import java.util.logging.Logger

class EthereumTransactionSyncer(
        private val etherscanTransactionsProvider: EtherscanTransactionsProvider
) : AbstractTransactionSyncer("ethereum_transaction_syncer") {

    private val logger = Logger.getLogger(this.javaClass.simpleName)
    private val disposables = CompositeDisposable()

    override fun onUpdateNonce(nonce: Long) {
        sync()
    }

    override fun onUpdateBalance(balance: BigInteger) {
        sync()
    }

    override fun onEthereumKitSynced() {
        sync()
    }

    private fun sync() {
        logger.info("---> sync() state: $state")

        if (state is EthereumKit.SyncState.Syncing) return

        state = EthereumKit.SyncState.Syncing()

        // gets transaction starting from last tx's block height
        etherscanTransactionsProvider
                .getTransactions(lastSyncBlockNumber + 1)
                .map { transactions ->
                    transactions.map { etherscanTransaction ->
                        NotSyncedTransaction(
                                hash = etherscanTransaction.hash,
                                transaction = RpcTransaction(
                                        hash = etherscanTransaction.hash,
                                        nonce = etherscanTransaction.nonce,
                                        blockHash = etherscanTransaction.blockHash,
                                        blockNumber = etherscanTransaction.blockNumber,
                                        transactionIndex = etherscanTransaction.transactionIndex,
                                        from = etherscanTransaction.from,
                                        to = etherscanTransaction.to,
                                        value = etherscanTransaction.value,
                                        gasPrice = etherscanTransaction.gasPrice,
                                        gasLimit = etherscanTransaction.gasLimit,
                                        input = etherscanTransaction.input
                                ),
                                timestamp = etherscanTransaction.timestamp
                        )
                    }
                }
                .subscribeOn(Schedulers.io())
                .subscribe({ notSyncedTransactions ->
                    logger.info("---> sync() onFetched: ${notSyncedTransactions.size}")

                    if (notSyncedTransactions.isNotEmpty()) {
                        delegate.add(notSyncedTransactions)

                        notSyncedTransactions.firstOrNull()?.transaction?.blockNumber?.let {
                            lastSyncBlockNumber = it
                        }
                    }

                    state = EthereumKit.SyncState.Synced()
                }, {
                    logger.info("---> sync() onError: ${it.message}")

                    state = EthereumKit.SyncState.NotSynced(it)
                })
                .let { disposables.add(it) }
    }

}
