package co.rsk.peg;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.ZonedDateTime;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP134;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP143;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class BridgeTest {

    private TestSystemProperties config = new TestSystemProperties();
    private Constants constants;
    private ActivationConfig activationConfig;

    @Before
    public void resetConfigToRegTest() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
    }

    @Test
    public void getLockingCap_before_RSKIP134_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_LOCKING_CAP.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void getLockingCap_after_RSKIP134_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.getLockingCap()).thenReturn(Coin.COIN);

        byte[] data = Bridge.GET_LOCKING_CAP.encode(new Object[]{ });
        byte[] result = bridge.execute(data);
        Assert.assertEquals(Coin.COIN.getValue(), ((BigInteger)Bridge.GET_LOCKING_CAP.decodeResult(result)[0]).longValue());
        // Also test the method itself
        Assert.assertEquals(Coin.COIN.getValue(), bridge.getLockingCap(new Object[]{ }));
    }

    @Test
    public void increaseLockingCap_before_RSKIP134_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.INCREASE_LOCKING_CAP.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void increaseLockingCap_after_RSKIP134_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.increaseLockingCap(any(), any())).thenReturn(true);

        byte[] data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ 1 });
        byte[] result = bridge.execute(data);
        Assert.assertTrue((boolean)Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        Assert.assertEquals(true, bridge.increaseLockingCap(new Object[]{ BigInteger.valueOf(1) }));

        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ 21_000_000 });
        result = bridge.execute(data);
        Assert.assertTrue((boolean)Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        Assert.assertEquals(true, bridge.increaseLockingCap(new Object[]{ BigInteger.valueOf(21_000_000) }));
    }

    @Test
    public void increaseLockingCap_invalidParameter() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Uses the proper signature but with no argument
        // The solidity decoder in the Bridge will convert the undefined argument as 0, but the initial validation in the method will reject said value
        byte[] data = Bridge.INCREASE_LOCKING_CAP.encodeSignature();
        byte[] result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature but appends invalid data type
        // This will be rejected by the solidity decoder in the Bridge directly
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("ab"));
        result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature and data type, but with an invalid value
        // This will be rejected by the initial validation in the method
        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ -1 });
        result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature and data type, but with a value that exceeds the long max value
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        Assert.assertNull(result);
    }

    @Test
    public void registerBtcCoinbaseTransaction_before_RSKIP143_activation() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{ value, zero, value, zero, zero });

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcCoinbaseTransaction_after_RSKIP143_activation() throws BlockStoreException, IOException, VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{ value, zero, value, zero, zero });

        bridge.execute(data);
        verify(bridgeSupportMock, times(1)).registerBtcCoinbaseTransaction(value, Sha256Hash.wrap(value), value, Sha256Hash.wrap(value), value);
    }

    @Test
    public void registerBtcCoinbaseTransaction_after_RSKIP143_activation_null_data() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature();
        byte[] result = bridge.execute(data);
        Assert.assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature(), Hex.decode("ab"));
        result = bridge.execute(data);
        Assert.assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        Assert.assertNull(result);
    }

    @Test
    public void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNull_throwsVMException() throws Exception {
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                null,
                null
        );

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(null).when(bridgeSupportMock).getRetiringFederation();

        // RSK Address: FederationTestUtils -> ECKey.fromPrivate(BigInteger.valueOf(PK + 1))
        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(999));
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(new RskAddress(key.getAddress())).when(rskTxMock).getSender();

        Bridge bridge = getBridgeInstance(rskTxMock, bridgeSupportMock, activations);

        try {
            executor.execute(bridge, null);
            fail("VMException should be thrown!");
        } catch (VMException vme) {
            Assert.assertEquals(
                    "Sender is not part of the active or retiring federations, so he is not enabled to call the function 'null'",
                    vme.getMessage()
            );
        }
    }

    @Test
    public void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNotNull_retiringFederationIsNotFromFederateMember_throwsVMException() throws Exception {
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                null,
                null
        );

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(101, 202, 303, 404, 505, 606),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(retiringFederation).when(bridgeSupportMock).getRetiringFederation();

        // RSK Address: FederationTestUtils -> ECKey.fromPrivate(BigInteger.valueOf(PK + 1))
        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(999));
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(new RskAddress(key.getAddress())).when(rskTxMock).getSender();

        Bridge bridge = getBridgeInstance(rskTxMock, bridgeSupportMock, activations);

        try {
            executor.execute(bridge, null);
            fail("VMException should be thrown!");
        } catch (VMException vme) {
            Assert.assertEquals(
                    "Sender is not part of the active or retiring federations, so he is not enabled to call the function 'null'",
                    vme.getMessage()
            );
        }
    }

    @Test
    public void activeAndRetiringFederationOnly_activeFederationIsFromFederateMember_OK() throws Exception {
        BridgeMethods.BridgeMethodExecutor decorate = mock(
                BridgeMethods.BridgeMethodExecutor.class
        );
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                decorate,
                null
        );

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(null).when(bridgeSupportMock).getRetiringFederation();

        // RSK Address: FederationTestUtils -> ECKey.fromPrivate(BigInteger.valueOf(PK + 1))
        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(101));
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(new RskAddress(key.getAddress())).when(rskTxMock).getSender();

        Bridge bridge = getBridgeInstance(rskTxMock, bridgeSupportMock, activations);

        executor.execute(bridge, null);

        verify(bridgeSupportMock, times(1)).getActiveFederation();
        verify(decorate, times(1)).execute(any(), any());
    }

    @Test
    public void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNotNull_retiringFederationIsFromFederateMember_OK() throws Exception {
        BridgeMethods.BridgeMethodExecutor decorate = mock(
                BridgeMethods.BridgeMethodExecutor.class
        );
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                decorate,
                null
        );

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersFromPks(101, 202, 303, 404, 505, 606),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(retiringFederation).when(bridgeSupportMock).getRetiringFederation();

        // RSK Address: FederationTestUtils -> ECKey.fromPrivate(BigInteger.valueOf(PK + 1))
        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(405));
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(new RskAddress(key.getAddress())).when(rskTxMock).getSender();

        Bridge bridge = getBridgeInstance(rskTxMock, bridgeSupportMock, activations);

        executor.execute(bridge, null);

        verify(bridgeSupportMock, times(1)).getActiveFederation();
        verify(decorate, times(1)).execute(any(), any());
    }

    /**
     * Gets a bridge instance mocking the transaction and BridgeSupportFactory
     *
     * @param bridgeSupportInstance Provide the bridgeSupport to be used
     * @param activationConfig      Provide the activationConfig to be used
     * @return Bridge instance
     */
    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig) {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(
                bridgeSupportInstance
        );

        Bridge bridge = new Bridge(
                PrecompiledContracts.BRIDGE_ADDR,
                constants,
                activationConfig,
                bridgeSupportFactoryMock
        );
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        return bridge;
    }

    /**
     * Gets a bridge instance mocking the BridgeSupportFactory
     *
     * @param transaction           Provide the Transaction to be used
     * @param bridgeSupportInstance Provide the bridgeSupport to be used
     * @param activationConfig      Provide the activationConfig to be used
     * @return Bridge instance
     */
    private Bridge getBridgeInstance(Transaction transaction, BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig) {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(
                bridgeSupportInstance
        );

        Bridge bridge = new Bridge(
                PrecompiledContracts.BRIDGE_ADDR,
                constants,
                activationConfig,
                bridgeSupportFactoryMock
        );
        bridge.init(transaction, getGenesisBlock(), null, null, null, null);

        return bridge;
    }

    @Deprecated
    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance) {
        return getBridgeInstance(bridgeSupportInstance, activationConfig);
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

}