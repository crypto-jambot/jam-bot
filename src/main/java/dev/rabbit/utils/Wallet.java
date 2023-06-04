package dev.rabbit.utils;

import dev.rabbit.config.WalletConfiguration;
import dev.rabbit.exception.RPCCallFailureException;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Component
public class Wallet {
    private final String seedPhrase;
    @Getter(AccessLevel.PRIVATE)
    private final List<Account> accounts = new ArrayList<>();

    @Autowired
    private Wallet(WalletConfiguration walletConfiguration, Web3j web3j) {
        this.seedPhrase = walletConfiguration.getSeedPhrase();

        // Generate a BIP32 master keypair from the mnemonic phrase
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(seedPhrase, null));

        Integer numAccounts = walletConfiguration.getNumberOfAccounts();
        boolean hasNumAccounts = ObjectUtils.isNotEmpty(numAccounts);

        for (int i = 0; !hasNumAccounts || i < numAccounts; i++) {
            Account account = new Account(Bip32ECKeyPair.deriveKeyPair(masterKeypair, getDerivativePath(i)), web3j);
            if (!hasNumAccounts && account.getLastKnownBalance().compareTo(new BigInteger("0")) == 0) break;
            accounts.add(account);
        }
    }

    private static int[] getDerivativePath(int index) {
        // Derivation path wanted: // m/44'/60'/0'/0
        return new int[]{44 | Bip32ECKeyPair.HARDENED_BIT, 60 | Bip32ECKeyPair.HARDENED_BIT, Bip32ECKeyPair.HARDENED_BIT, 0, index};
    }

    public Account getAccount(int index) {
        return this.getAccounts().get(index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Seed Phrase: ").append(seedPhrase).append("\n");
        for (int i = 0; i < accounts.size(); i++) {
            sb.append("Account ").append(i).append(" : ").append(accounts.get(i).toString()).append("\n");
        }
        return sb.toString();
    }

    @Getter
    public static class Account {
        private final String address;
        private final String privateKey;
        private final Credentials credentials;
        private final BigInteger lastKnownBalance;

        private Account(ECKeyPair keyPair, Web3j web3j) {
            this.credentials = Credentials.create(keyPair);
            this.privateKey = keyPair.getPrivateKey().toString(16);
            this.address = this.credentials.getAddress();
            try {
                lastKnownBalance = web3j.ethGetBalance(this.address, DefaultBlockParameter.valueOf("latest")).send().getBalance();
            } catch (IOException e) {
                throw RPCCallFailureException.of("Failed to retrieve the balance of the account: " + this.address, e);
            }
        }

        @Override
        public String toString() {
            return "Address: " + address + ", PrivateKey: " + privateKey + ", Last Known Balance: " + lastKnownBalance;
        }
    }
}
