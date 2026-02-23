# TCP Network Simulator
A simple network simulator for testing TCP functionality, including connection establishment, data transmission, and congestion control mechanisms. 
    
## Features
### TCP Connection Management
 + Establishes connection with a three-way handshake
 + Behavior is managed with a state machine
 + Tears down connection with a four-way handshake

### Reliable Data Transfer
 + Tracks sequence numbers and acknoledgements
 + Uses a checksum check to ensure data integrity (there is a default 0.01 corruption rate on the data in this simulator)
 + 

### Reno Congestion Control
 + Dynamic congestion window management
 + Implements slow start, 
 + Fast retransmission

### Discrete Event Simulation
 + 
 
## Usage
### How to Run
1.  Before starting the network simulator, you'll need to prepare a topology file to set the the network topology.
    To simplify the network without adding too much and take away from its main purpose (which is to run tests on my TCP implementation), there are only three entities in this network: the hosts, routers and links. 

    In the topology file you'll be giving to the network should have lines similar to below to represent hosts, routers, links, and the configurations for the links.  
    For hosts, the format should be: `host <host_name> <host_id>`.  
    For routers, the format should be: `router <router_name> <router_id>`.  
    For links, the format should be: `link <node_name> <node_name> <buffer_size> <bandwidth> <latency> <loss_rate>`
     + buffer size is in bytes, bandwidth is in Mbps, latency is in ms, and loss rate has the lowest at 0.0 and highest at 1.0

    The topology file should look like the following example:  
    ```
    host h1 1
    host h2 2
    router r1 3
    router r2 4
    link r1 h1 64000 1000 1 0
    link r2 h2 64000 1000 1 0
    link r1 r2 2048 0.5 15 0
    ```

    Two basic topologies, dumbbell and triangle is provided in the topo folder in this repo.

2.  Now you can run the simulator! The very first thing you need to do is to set up the network.  
    Run `setup <your_topo_file>` then the network is set up.
    
3.  The tests that can be run in this simulator are sweep tests. On the highest level, there are two options. You can 
    run them with your own configurations, or have the network run with built in configurations.
    To run tests with built in configurations, run  
    `run -s <start_host_ID> -d <destination_host_ID> -l <link_to_do_sweep_tests_on> -t <test_to_run>`
    + <test_to_run> includes `latency`, `bandwidth`, `buffersize` and `lossrate`. 

    The above command and flags are required in order to run any tests. There are also optional flags you can add to customize the tests more:  
    `-r <lowest>:<highest>:<step>` : add range of the sweeps and step size. For example, `-r 10:100:20` means that the tests start at 10 and end at 100, with a step size of 20. So that means a total of 6 tests will be run and the values are 10, 30, 50, 70, 90, 100  
    `f <file_size_in_KB>` : choose how large the file to be sent is. Default is 1MB  
    `-n <file_name>` : name of the file you want to run the tests with.  
    `-c` : runs the tests with the original configurations you set up in your topo file  
    `-v` : verbose mode, will print out every send and receive packet information and timestamps  

4.  Other commands
    `showconfig` : shows the configuration of the network and the topology
    `help` : shows you what commands and flags you can use
    `exit` : exits the simulator

### The Default Tests
1. For buffer size:
2. For bandwidth:    

### Example Output
Command:  
`run -s 1 -d 4 -t latency -l r1:r2 -f 10`
Result: 
```
================================================================
                    Latency Test 0 (1.0 ms)
================================================================
Bottleneck Link Configuration:
    Latency:                                           1.0 ms
    Bandwidth:                                         10.0 Mbps
    Buffer Size:                                       75.0 KB
    Loss Rate:                                         0.0 %


Sender Stats: 
    Amount of data transferred:                        10.240 KB
    Number of packets sent:                            11
    Number of packets received:                        10
    Number of retransmissions:                         0
    Number of duplicate acknowledgements:              0
    Number of packets discarded (incorrect checksum):  0

Receiver Stats:
    Amount of data received:                           10.240 KB
    Number of packets sent:                            10
    Number of packets received:                        11
    Number of out-of-sequence packets discarded:       0
    Number of packets discarded (incorrect checksum):  0

Final Stat:
    Total Time:                                        0.034 sec
    Throughput:                                        2.379 Mbps
    Average RTT:                                       8.793 ms
    Average congestion window size:                    3.866 segments
    Retransmission Ratio:                              0.000
    Total Timeouts:                                    0
    Total Fast Retransmissions:                        0

================================================================
                    Latency Test 1 (5.0 ms)
================================================================
Bottleneck Link Configuration:
    Latency:                                           5.0 ms
    Bandwidth:                                         10.0 Mbps
    Buffer Size:                                       75.0 KB
    Loss Rate:                                         0.0 %


Sender Stats:
    Amount of data transferred:                        10.240 KB
    Number of packets sent:                            11
    Number of packets received:                        10
    Number of retransmissions:                         0
    Number of duplicate acknowledgements:              0
    Number of packets discarded (incorrect checksum):  0

Receiver Stats:
    Amount of data received:                           10.240 KB
    Number of packets sent:                            10
    Number of packets received:                        11
    Number of out-of-sequence packets discarded:       0
    Number of packets discarded (incorrect checksum):  0

Final Stat:
    Total Time:                                        0.070 sec
    Throughput:                                        1.163 Mbps
    Average RTT:                                       16.793 ms
    Average congestion window size:                    3.866 segments
    Retransmission Ratio:                              0.000
    Total Timeouts:                                    0
    Total Fast Retransmissions:                        0

================================================================
                    Latency Test 2 (10.0 ms)
================================================================
Bottleneck Link Configuration:
    Latency:                                           10.0 ms
    Bandwidth:                                         10.0 Mbps
    Buffer Size:                                       75.0 KB
    Loss Rate:                                         0.0 %


Sender Stats:
    Amount of data transferred:                        10.240 KB
    Number of packets sent:                            11
    Number of packets received:                        10
    Number of retransmissions:                         0
    Number of duplicate acknowledgements:              0
    Number of packets discarded (incorrect checksum):  0

Receiver Stats:
    Amount of data received:                           10.240 KB
    Number of packets sent:                            10
    Number of packets received:                        11
    Number of out-of-sequence packets discarded:       0
    Number of packets discarded (incorrect checksum):  0

Final Stat:
    Total Time:                                        0.115 sec
    Throughput:                                        0.710 Mbps
    Average RTT:                                       26.793 ms
    Average congestion window size:                    3.866 segments
    Retransmission Ratio:                              0.000
    Total Timeouts:                                    0
    Total Fast Retransmissions:                        0

================================================================
                    Latency Test 3 (25.0 ms)
================================================================
Bottleneck Link Configuration:
    Latency:                                           25.0 ms
    Bandwidth:                                         10.0 Mbps
    Buffer Size:                                       75.0 KB
    Loss Rate:                                         0.0 %


Sender Stats:
    Amount of data transferred:                        10.240 KB
    Number of packets sent:                            11
    Number of packets received:                        10
    Number of retransmissions:                         0
    Number of duplicate acknowledgements:              0
    Number of packets discarded (incorrect checksum):  0

Receiver Stats:
    Amount of data received:                           10.240 KB
    Number of packets sent:                            10
    Number of packets received:                        11
    Number of out-of-sequence packets discarded:       0
    Number of packets discarded (incorrect checksum):  0

Final Stat:
    Total Time:                                        0.250 sec
    Throughput:                                        0.327 Mbps
    Average RTT:                                       56.793 ms
    Average congestion window size:                    3.866 segments
    Retransmission Ratio:                              0.000
    Total Timeouts:                                    0
    Total Fast Retransmissions:                        0

================================================================
                    Latency Test 4 (50.0 ms)
================================================================
Bottleneck Link Configuration:
    Latency:                                           50.0 ms
    Bandwidth:                                         10.0 Mbps
    Buffer Size:                                       75.0 KB
    Loss Rate:                                         0.0 %


Sender Stats:
    Amount of data transferred:                        10.240 KB
    Number of packets sent:                            11
    Number of packets received:                        10
    Number of retransmissions:                         0
    Number of duplicate acknowledgements:              0
    Number of packets discarded (incorrect checksum):  0

Receiver Stats:
    Amount of data received:                           10.240 KB
    Number of packets sent:                            10
    Number of packets received:                        11
    Number of out-of-sequence packets discarded:       0
    Number of packets discarded (incorrect checksum):  0

Final Stat:
    Total Time:                                        0.475 sec
    Throughput:                                        0.172 Mbps
    Average RTT:                                       106.793 ms
    Average congestion window size:                    3.866 segments
    Retransmission Ratio:                              0.000
    Total Timeouts:                                    0
    Total Fast Retransmissions:                        0

================================================================
                   Latency Test 5 (100.0 ms)
================================================================
Bottleneck Link Configuration:
    Latency:                                           100.0 ms
    Bandwidth:                                         10.0 Mbps
    Buffer Size:                                       75.0 KB
    Loss Rate:                                         0.0 %


Sender Stats:
    Amount of data transferred:                        10.240 KB
    Number of packets sent:                            11
    Number of packets received:                        10
    Number of retransmissions:                         0
    Number of duplicate acknowledgements:              0
    Number of packets discarded (incorrect checksum):  0

Receiver Stats:
    Amount of data received:                           10.240 KB
    Number of packets sent:                            10
    Number of packets received:                        11
    Number of out-of-sequence packets discarded:       0
    Number of packets discarded (incorrect checksum):  0

Final Stat:
    Total Time:                                        0.925 sec
    Throughput:                                        0.089 Mbps
    Average RTT:                                       206.793 ms
    Average congestion window size:                    3.866 segments
    Retransmission Ratio:                              0.000
    Total Timeouts:                                    0
    Total Fast Retransmissions:                        0
```
## Notes
### Personal Log
 + At first I tried to use threads to set up the network, which made sense to me because the sender and receiver should both be actively listening and sending. However, I soon realized that debugging such a multi-threaded approach may cause unwanted trouble for me and cause me to lose focus as my main goal is to implement TCP protocol itself. So, I switched to making it a discrete event simulation.



