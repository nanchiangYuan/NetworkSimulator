# TCP Network Simulator
A simple network simulator for testing TCP functionality, including connection establishment, data transmission, and congestion control mechanisms. 
    
## Features
### TCP Connection Management
 + Establishes connection with a three-way handshake
 + Behavior is managed with a state machine
 + Tears down connection with a four-way handshake

### Reliable Data Transfer
 + Tracks sequence numbers and ack numbers
 + Uses a checksum check to ensure data integrity (there is a default 0.01 corruption rate on the data in this simulator)
 + 

### Reno Congestion Control
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
    Run
    `setup <your_topo_file>`
    then the network is set up.
    
3.  The tests that can be run in this simulator are sweep tests. On the highest level, there are two options. You can 
    run them with your own configurations, or have the network run with built in configurations.
    To run tests with built in configurations, run
    `run -s <start_host_ID> -d <destination_host_ID> -l <link_to_do_sweep_tests_on> -t <test_to_run>`
    <test_to_run> includes `latency`, `bandwidth`, `buffersize` and `lossrate`. 

    The above command and flags are required in order to run any tests. There are also optional flags you can add to customize the tests more:
    '-r <lowest>:<highest>:<step>`


4.  Other commands
    `showconfig` : shows the configuration of the network and the topology
    `help` : shows you what commands and flags you can use
    `exit` : exits the simulator

### The Tests
    

### Example Output

## Notes
### Personal Log
 + At first I tried to use threads to set up the network, which made sense to me because the sender and receiver should both be actively listening and sending. However, I soon realized that debugging such a multi-threaded approach may cause unwanted trouble for me and cause me to lose focus as my main goal is to implement TCP protocol itself. So, I switched to making it a discrete event simulation.



